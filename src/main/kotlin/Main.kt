import backend.llvm.LLVMEmitter
import frontend.*
import hir.HirEmitter
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.file.*
import kotlin.io.path.absolute
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

fun compile() : String{
    val source = Source.new("test.si")
    val symTable = SymTable()
    val lexer = Lexer(source, symTable)

    val parser = Parser(lexer, symTable)
    val mod = parser.parseModule()

    val binder = Binder()
    binder.bind(mod)

    val tyTable = TyTable()
    val checker = TypeChecker(tyTable)
    checker.check(mod)

    val llvmEmitter = LLVMEmitter()
    val llvmModule = llvmEmitter.emitModule(mod)

    return llvmModule.dump("out.llvm")
}

fun main(args: Array<String>) {

    val code = compile()

    val fos = FileOutputStream("out.ll")
    fos.write(code.toByteArray(Charset.defaultCharset()))
    fos.flush()
    fos.close()

    val result = ProcessBuilder("/opt/homebrew/Cellar/llvm/17.0.5/bin/lli", "out.ll")
        .inheritIO()
        .start()
        .waitFor()


    println(result)
    println("xxx")


    watchFolderForChanges(listOf(Path.of("."))) {
        if(it.extension != "si") return@watchFolderForChanges

        val code = compile()

        val fos = FileOutputStream("out.ll")
        fos.write(code.toByteArray(Charset.defaultCharset()))
        fos.flush()
        fos.close()

        val result = ProcessBuilder("/opt/homebrew/Cellar/llvm/17.0.5/bin/lli", "out.ll")
            .inheritIO()
            .start()
            .waitFor()

        println(result)

    }

    //val hirEmitter = HirEmitter(symTable)
    //hirEmitter.emitModule(mod)

    //hirEmitter.list()
}



fun getAbsDirsRec(dirs: List<Path>, result: MutableList<Path>) {
    for (dir in dirs) {
        result.add(dir.absolute())
        dir.toFile().listFiles()?.forEach {
            if (it.isDirectory) {
                getAbsDirsRec(listOf(it.toPath()), result)
            }
        }
    }
}


fun watchFolderForChanges(dirs: List<Path>, callback: (Path) -> Unit) {
    val watchService = FileSystems.getDefault().newWatchService()

    val absDirs = mutableListOf<Path>()
    getAbsDirsRec(dirs, absDirs)
    absDirs.forEach {
        val dir = if (it.isRegularFile()) it.parent else it
        dir.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY
        )
    }

    while (true) {
        val watchKey: WatchKey
        try {
            watchKey = watchService.take()

            // Required to avoid double fires
            Thread.sleep(50)
        } catch (ex: InterruptedException) {
            return
        }

        watchKey.pollEvents().forEach { event ->
            val kind = event.kind()

            @Suppress("UNCHECKED_CAST")
            val ev = event as WatchEvent<Path>
            val filename = ev.context()
            val parentDirectory = watchKey.watchable() as Path
            val fullPath = parentDirectory.resolve(filename)

            if (filename.name.endsWith('~')) {
                return@forEach
            }

            var matches = false
            for (absDir in absDirs) {
                if (fullPath.startsWith(absDir)) {
                    matches = true
                }
            }
            if (!matches) return@forEach

            when (kind) {
                StandardWatchEventKinds.ENTRY_CREATE -> {
                    callback(fullPath)
                }

                StandardWatchEventKinds.ENTRY_MODIFY -> {
                    callback(fullPath)
                }
            }
        }

        if (!watchKey.reset()) {
            break
        }
    }
}
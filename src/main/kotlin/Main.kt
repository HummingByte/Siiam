import backend.llvm.LLVMEmitter
import frontend.*
import hir.HirEmitter
import java.io.FileOutputStream
import java.nio.charset.Charset

fun main(args: Array<String>) {

    val source = Source.new("test.si")
    val symTable = SymTable()
    val lexer = Lexer(source, symTable)

    var parser = Parser(lexer, symTable)

    val mod = parser.parseModule()

    val binder = Binder()
    binder.bind(mod)

    val tyTable = TyTable()
    val checker = TypeChecker(tyTable)
    checker.check(mod)

    val llvmEmitter = LLVMEmitter()
    val llvmModule = llvmEmitter.emitModule(mod)

    val code = llvmModule.dump("out.llvm")

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

    //val hirEmitter = HirEmitter(symTable)
    //hirEmitter.emitModule(mod)

    //hirEmitter.list()
}
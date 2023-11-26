package backend.llvm

import backend.llvm.type.LLVMFunctionType
import kotlin.text.StringBuilder

class LLVMModule {
    private val functions = mutableMapOf<String, LLVMFunction>()

    fun createFunction(name: String, ty: LLVMFunctionType ) : LLVMFunction {
        if(functions.containsKey(name)) throw RuntimeException("function already exisis");
        val newFunc = LLVMFunction(name, ty)
        functions[name] = newFunc
        return newFunc
    }

    fun dump(name: String) : String{
        val sb = StringBuilder()
        val cp = CodePrinter(sb)

        for( fn in functions.values ){
            fn.dump(cp)
            cp.nl()
        }

        return sb.toString()
    }

}
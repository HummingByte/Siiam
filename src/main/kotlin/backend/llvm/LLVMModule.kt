package backend.llvm

import backend.llvm.type.LLVMFunctionType
import backend.llvm.type.LLVMStructType
import kotlin.text.StringBuilder

class LLVMModule {
    private val functions = mutableMapOf<String, LLVMFunction>()
    private val structs = mutableMapOf<String, LLVMStructType>()

    fun createFunction(name: String, ty: LLVMFunctionType ) : LLVMFunction {
        if(functions.containsKey(name)) throw RuntimeException("function already exisis");
        val newFunc = LLVMFunction(name, ty)
        functions[name] = newFunc
        return newFunc
    }

    fun addStruct(name: String, struct: LLVMStructType){
        structs[name] = struct
    }

    fun dump(name: String) : String{
        val sb = StringBuilder()
        val cp = CodePrinter(sb)

        for( struct in structs.values ){
            struct.dump(cp)
            cp.nl()
        }
        cp.forceNl()
        cp.forceNl()
        for( fn in functions.values ){
            fn.dump(cp)
            cp.nl()
        }


        return sb.toString()
    }

}
package backend.llvm

import backend.llvm.type.*
import frontend.FnTy

class LLVMFunction(
    name: String,
    ty: LLVMFunctionType
) : LLVMValue(ty){
    val params: List<LLVMArgument>
    val basicBlocks = LinkedHashMap<String, LLVMBasicBlock>()
    var nameIdx = 0
    init {
        this.name = name
        val newArgs = ty.paramTys.map{
            LLVMArgument(this, it)
        }

        params = newArgs
    }

    fun createName() : String{
        return ".${nameIdx++}"
    }

    fun createBasicBlock(name: String) : LLVMBasicBlock {
        var suffixName = name
        var idx = 2
        while(basicBlocks.containsKey(suffixName)){
            suffixName = "$name-${idx++}"
        }
        val newBasicBlock = LLVMBasicBlock(this, suffixName)
        basicBlocks[suffixName] = newBasicBlock
        return newBasicBlock
    }

    override fun dump(cp: CodePrinter) {
        cp += "define "
        val fnType = ty as LLVMFunctionType
        fnType.retTy.dump(cp)
        cp.sep()
        cp += "@${name}"
        cp += "("
        var sep = ""
        for(param in params){
            cp += sep
            param.ty.dump(cp)
            cp.sep()
            cp += "%${param.name}"
            sep = ", "
        }
        cp += "){"
        cp.nl()

        for( bb in basicBlocks.values){
            bb.dump(cp)
            cp.nl()
        }

        cp.deIndent()
        cp += "}"
    }

    override fun remit(cp : CodePrinter, withType: Boolean){
        if(withType){
            ty.dump(cp)
            cp.sep()
        }
        cp += "@$name"
    }
}
abstract class LLVMValue(val ty: LLVMType) : LLVMDump {
    var name: String? = null

    override fun remit(cp : CodePrinter, withType: Boolean){
        if(withType){
            ty.dump(cp)
            cp.sep()
        }
        cp += "%$name"
    }
}

class LLVMArgument(val function: LLVMFunction, ty: LLVMType) : LLVMValue(ty) {

    override fun dump(cp: CodePrinter) {
        cp += "%$name"
    }
}

class ConstInt(val value: Int, ty: LLVMIntType) : LLVMValue(ty){
    override fun dump(cp: CodePrinter) {
        cp += value.toString()
    }

    override fun remit(cp: CodePrinter, withType: Boolean) {
        if(withType){
            ty.dump(cp)
            cp.sep()
        }
        cp += value.toString()
    }
}

object LLVMNothing : LLVMValue(LLVMVoidType){
    override fun dump(cp: CodePrinter) {
        throw RuntimeException("Nothing dumpable!")
    }
}
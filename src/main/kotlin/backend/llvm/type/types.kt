package backend.llvm.type

import backend.llvm.CodePrinter

interface LLVMDump{
    fun dump(cp: CodePrinter)
    fun remit(cp: CodePrinter, withType: Boolean = true)
}

interface LLVMType : LLVMDump
data class LLVMFunctionType(val retTy: LLVMType, val paramTys : List<LLVMType>, val isVarArg : Boolean) : LLVMType{
    companion object{
        private var map = HashMap<LLVMFunctionType, LLVMFunctionType>()
        fun get(retTy: LLVMType, paramTys : List<LLVMType>, isVarArg : Boolean) : LLVMFunctionType{
            val newType = LLVMFunctionType(retTy, paramTys, isVarArg)
            val oldType = map[newType]
            if(oldType != null){
                return oldType
            }
            return newType
        }
    }

    override fun dump(cp: CodePrinter) {
        TODO("Not yet implemented")
    }

    override fun remit(cp: CodePrinter, withType: Boolean) {
        dump(cp)
    }
}

object LLVMVoidType : LLVMType{
    override fun dump(cp: CodePrinter) {
        cp += "void"
    }

    override fun remit(cp: CodePrinter, withType: Boolean) {
        TODO("Not yet implemented")
    }
}

object LLVMBBType : LLVMType{
    override fun dump(cp: CodePrinter) {
        cp += "label"
    }

    override fun remit(cp: CodePrinter, withType: Boolean) {
        TODO("Not yet implemented")
    }
}
data class LLVMIntType(val width: Int) : LLVMType{
    companion object{
        private val intTys = Array(128){
            LLVMIntType(it)
        }

        fun get(width : Int) : LLVMIntType{
            return intTys[width]
        }
    }

    override fun dump(cp: CodePrinter) {
        cp += "i$width"
    }

    override fun remit(cp: CodePrinter, withType: Boolean) {
        dump(cp)
    }
}

data class LLVMStructType(val name: String, val memberTys: List<LLVMType>) : LLVMType{
    override fun dump(cp: CodePrinter) {
        cp += "%$name = type {"
        var sep = ""
        for( memberTy in memberTys ){
            cp += sep
            memberTy.remit(cp)
            sep = ", "
        }
        cp += "}"
        cp.nl()
    }

    override fun remit(cp: CodePrinter, withType: Boolean) {
        cp += "%$name"
    }
}

data class LLVMPointerType(val elemTy: LLVMType) : LLVMType{
    companion object{
        private var map = HashMap<LLVMType, LLVMPointerType>()
        fun get(elementTy : LLVMType) : LLVMPointerType{
            val newType = LLVMPointerType(elementTy)
            val oldType = map[newType]
            if(oldType != null){
                return oldType
            }
            return newType
        }
    }

    override fun dump(cp: CodePrinter) {
        elemTy.dump(cp)
        cp += "*"
    }

    override fun remit(cp: CodePrinter, withType: Boolean) {
        dump(cp)
    }
}
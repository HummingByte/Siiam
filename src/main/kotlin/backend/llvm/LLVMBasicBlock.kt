package backend.llvm

import backend.llvm.type.*

class LLVMBasicBlock(
    val fn: LLVMFunction,
    name: String
) : LLVMValue(LLVMBBType) {
    init {
        this.name = name
    }

    private val insts = mutableListOf<LLVMInst>()

    fun size() : Int{
        return insts.size
    }

    fun addInst(inst: LLVMInst) : LLVMInst{
        return addInst(inst, insts.size)
    }
    fun addInst(inst: LLVMInst, offset: Int) : LLVMInst{
        insts.add(offset, inst)
        if(inst.name == null){
            inst.name = fn.createName()
        }
        return inst
    }

    override fun dump(cp: CodePrinter) {
        cp += "$name:"
        cp.indent()
        cp.nl()

        for( inst in insts ){
            inst.dump(cp)
            cp.nl()
        }

        cp.deIndent()
    }
}


abstract class LLVMInst(ty: LLVMType) : LLVMValue(ty)

class AllocaInst(val elemTy: LLVMType) : LLVMInst(LLVMPointerType.get(elemTy)){
    override fun dump(cp: CodePrinter) {
        cp += "%$name = alloca "
        elemTy.dump(cp)
    }
}
class StoreInst(val value: LLVMValue, val ptr: LLVMValue) : LLVMInst(LLVMVoidType){
    override fun dump(cp: CodePrinter) {
        cp += "store "
        value.remit(cp)
        cp += ", "
        ptr.remit(cp)
        cp += ", align 4"
    }
}
class LoadInst(val ptr: LLVMValue) : LLVMInst((ptr.ty as LLVMPointerType).elemTy){
    override fun dump(cp: CodePrinter) {
        cp += "%$name = load "
        ty.dump(cp)
        cp += ", "
        ptr.remit(cp)
    }
}
class InfixInst(val kind: InfixKind, val lhs: LLVMValue, val rhs: LLVMValue) : LLVMInst(lhs.ty){

    init {
        assert(lhs.ty === rhs.ty)
    }
    override fun dump(cp: CodePrinter) {
        cp += "%$name = ${kind.op} "
        lhs.remit(cp)
        cp += ", "
        rhs.remit(cp, withType = false)
    }
}
class ReturnInst(val value: LLVMValue?) : LLVMInst(LLVMVoidType) {
    override fun dump(cp: CodePrinter) {
        cp += "ret "
        this.value?.remit(cp)
    }
}
class CallInst(val callee: LLVMValue, val args: List<LLVMValue>) : LLVMInst((callee.ty as LLVMFunctionType).retTy){
    override fun dump(cp: CodePrinter) {
        remit(cp, false)
        cp += " = call "
        val fnType = callee.ty as LLVMFunctionType
        fnType.retTy.dump(cp)
        cp.sep()
        callee.remit(cp, false)
        cp += "("
        var sep = ""
        for(arg in args){
            cp += sep
            arg.remit(cp)
            sep = ", "
        }
        cp += ")"
    }
}

class CondBranchInst(val cond: LLVMValue, val trueBB: LLVMBasicBlock, val falseBB: LLVMBasicBlock) : LLVMInst(LLVMVoidType){
    override fun dump(cp: CodePrinter) {
        cp += "br "
        cond.remit(cp)
        cp += ", "
        trueBB.remit(cp)
        cp += ", "
        falseBB.remit(cp)
    }
}
class BranchInst(val bb: LLVMBasicBlock) : LLVMInst(LLVMVoidType){
    override fun dump(cp: CodePrinter) {
        cp += "br "
        bb.remit(cp)
    }
}

class PhiInst(val pairs: Array<out Pair<LLVMValue, LLVMBasicBlock>>) : LLVMInst(pairs.first().first.ty){
    override fun dump(cp: CodePrinter) {
        remit(cp, false)
        cp += " = phi "
        ty.dump(cp)
        var sep = " "
        for(pair in pairs){
            cp += sep
            cp += "["
            pair.first.remit(cp, false)
            cp += ", "
            pair.second.remit(cp, false)
            cp += "]"
            sep = ", "
        }
    }
}
enum class InfixKind(val op: String){
    Add("add"),
    Sub("sub"),
    Mul("mul"),
    Div("div");
}
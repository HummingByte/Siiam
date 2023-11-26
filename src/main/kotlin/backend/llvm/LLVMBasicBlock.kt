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
    inline fun <reified T: LLVMInst> addInst(inst: T) : T{
        addInstImpl(inst)
        if(inst.name == null){
            inst.name = fn.createName()
        }
        return inst
    }

    fun addInstImpl(inst: LLVMInst){
        insts.add(inst)
    }
    fun createAlloca(pointerTy: LLVMType) : LLVMValue {
        return addInst(AllocaInst(pointerTy))
    }
    fun createStore(value: LLVMValue, ptr: LLVMValue) : LLVMValue {
        return addInst(StoreInst(value, ptr))
    }

    fun createLoad(ptr: LLVMValue) : LLVMValue {
        return addInst(LoadInst(ptr))
    }

    fun createAdd(lhs: LLVMValue, rhs: LLVMValue) : LLVMValue {
        return addInst(InfixInst(InfixKind.Add, lhs, rhs))
    }

    fun createSub(lhs: LLVMValue, rhs: LLVMValue) : LLVMValue {
        return addInst(InfixInst(InfixKind.Sub, lhs, rhs))
    }

    fun createMul(lhs: LLVMValue, rhs: LLVMValue) : LLVMValue {
        return addInst(InfixInst(InfixKind.Mul, lhs, rhs))
    }

    fun createDiv(lhs: LLVMValue, rhs: LLVMValue) : LLVMValue {
        return addInst(InfixInst(InfixKind.Div, lhs, rhs))
    }

    fun createRet(value: LLVMValue?){
        addInstImpl(ReturnInst(value))
    }

    fun createCall(callee: LLVMValue, args: List<LLVMValue>) : LLVMValue {
        return addInst(CallInst(callee, args))
    }

    fun createCondBr(cond: LLVMValue, trueBB: LLVMBasicBlock, falseBB: LLVMBasicBlock){
        return addInstImpl(CondBranchInst(cond, trueBB, falseBB))
    }

    fun createBr(bb: LLVMBasicBlock){
        return addInstImpl(BranchInst(bb))
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
enum class InfixKind(val op: String){
    Add("add"),
    Sub("sub"),
    Mul("mul"),
    Div("div");
}
package backend.llvm

import backend.llvm.type.LLVMType


class LLVMBuilder{
    private lateinit var bb: LLVMBasicBlock
    private var offset: Int = -1

    fun set(bb: LLVMBasicBlock){
        set(bb, bb.size())
    }
    fun set(bb: LLVMBasicBlock, offset: Int){
        this.bb = bb
        this.offset = offset
    }

    fun prev(){
        offset--
    }

    fun addInst(inst: LLVMInst) : LLVMInst{
        bb.addInst(inst, offset)
        offset++
        return inst
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
        addInst(ReturnInst(value))
    }

    fun createCall(callee: LLVMValue, args: List<LLVMValue>) : LLVMValue {
        return addInst(CallInst(callee, args))
    }

    fun createCondBr(cond: LLVMValue, trueBB: LLVMBasicBlock, falseBB: LLVMBasicBlock){
        addInst(CondBranchInst(cond, trueBB, falseBB))
    }

    fun createBr(bb: LLVMBasicBlock){
        addInst(BranchInst(bb))
    }

    fun createPhi(vararg pairs : Pair<LLVMValue, LLVMBasicBlock>) : LLVMValue{
        return addInst(PhiInst(pairs))
    }

    fun createPhi(lhsValue: LLVMValue, lhsBB: LLVMBasicBlock, rhsValue: LLVMValue, rhsBB: LLVMBasicBlock) : LLVMValue{
        return createPhi(Pair(lhsValue, lhsBB), Pair(rhsValue, rhsBB))
    }
}
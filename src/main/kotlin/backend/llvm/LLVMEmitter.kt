package backend.llvm

import backend.llvm.type.*
import frontend.FnTy
import frontend.PrimTy
import frontend.SymTable
import frontend.Ty
import frontend.*
import frontend.unreachable

enum class LLVMEmitterMode{
    Decl,
    Def
}

class LLVMEmitter(){
    val module = LLVMModule()
    private var mode = LLVMEmitterMode.Decl
    val type2llvm = mutableMapOf<Ty, LLVMType>()

    val fns = HashMap<String, LLVMFunction>()
    val structs = HashMap<String, LLVMStructType>()
    val struct2def = HashMap<Ty, String>()
    val decl2slot = HashMap<Decl, LLVMValue>()

    lateinit var fn: LLVMFunction
    lateinit var bb: LLVMBasicBlock

    fun getFun(name : String) : LLVMFunction {
        return fns[name]!!
    }

    fun addDecl(decl: FnDecl, value: LLVMFunction){
        val name = decl.ident.name
        fns[name] = value
        decl2slot[decl] = value
    }

    fun addDecl(decl: StructDecl, value: LLVMStructType){
        val name = decl.ident.name
        structs[name] = value
    }

    fun addDecl(decl: Decl, value: LLVMValue){
        decl2slot[decl] = value
    }

    fun getDecl(decl: Decl) : LLVMValue {
        return decl2slot[decl]!!
    }

    fun emitOptTy(ty: Ty?) : LLVMType {
        return when(ty){
            null -> LLVMVoidType
            else -> emitTy(ty)
        }
    }

    fun emitTy(ty: Ty) : LLVMType {
        return when(ty){
            is PrimTy -> {
                when(ty.kind){
                    PrimTyKind.I32 -> LLVMIntType(32)
                    PrimTyKind.I64 -> LLVMIntType(64)
                    else -> unreachable()
                }
            }
            is FnTy -> {
                LLVMFunctionType.get(emitTy(ty.retTy!!), ty.params.map { emitTy(it) }, false)
            }
            is StructTy -> {
                val testTy = type2llvm[ty]
                if( testTy != null ){
                    testTy
                }else{
                    val llvmMemberTys = mutableListOf<LLVMType>()
                    val llvmStructTy = LLVMStructType(llvmMemberTys)
                    type2llvm[ty] = llvmStructTy

                    for( memberTy in ty.members){
                        llvmMemberTys.add(emitTy(memberTy))
                    }

                    llvmStructTy
                }
            }
            else -> {
                println("{$ty}")
                unreachable()
            }
        }
    }

    fun emitModule(module: Module) : LLVMModule{
        mode = LLVMEmitterMode.Decl
        for( item in module.items){
            emitDecl(item)
        }
        
        mode = LLVMEmitterMode.Def
        for( item in module.items){
            emitDecl(item)
        }

        return this.module
    }

    fun emitDecl(decl: Decl){
        if( mode == LLVMEmitterMode.Decl){

            when(decl){
                is FnDecl -> {
                    val fnType = decl.ty as FnTy
                    val params = mutableListOf<LLVMType>()
                    for(param in fnType.params){
                        params.add(emitTy(param))
                    }
                    val fnc = module.createFunction(decl.ident.name, emitTy(fnType) as LLVMFunctionType)
                    addDecl(decl, fnc)
                }
                is StructDecl -> {
                    val structTy = decl.ty!!
                    val structTyDef = emitTy(structTy) as LLVMStructType
                    addDecl(decl, structTyDef)
                }
                else -> {}
            }
        }else{
            when(decl){
                is FnDecl -> {
                    fn = fns[decl.ident.name]!!
                    bb = fn.createBasicBlock("entry")

                    for( (param, llvmParam) in decl.params.zip(fn.params)){
                        llvmParam.name = param.ident.name
                        val slot = bb.createAlloca(llvmParam.ty)
                        bb.createStore(llvmParam, slot)
                        decl2slot[param] = slot
                    }

                    remitExpr(decl.body)
                }
                else -> {}
            }
        }
    }

    fun emitStmt(stmt: Stmt) : LLVMValue {
        return when(stmt){
            is ExprStmt -> {
                remitExpr(stmt.expr)
            }
            is LetStmt ->{
                val init = stmt.init
                if( init != null ){
                    val initVal = remitExpr(init)
                    addDecl(stmt.localDecl, initVal)
                }else{
                    unreachable()
                }
    
                LLVMNothing
            }
            else -> unreachable()
        }
    }

    fun remitExpr(expr: Expr) : LLVMValue {
        return when(expr){
            is LiteralExpr -> {
                when(val literal = expr.literal){
                    is IntLiteral -> ConstInt(literal.value, LLVMIntType.get(32))
                    else -> unreachable()
                }
            }
            is InfixExpr -> {
                val lhsVal = remitExpr(expr.lhs)
                val rhsVal = remitExpr(expr.rhs)

                when(val op = expr.op){
                    Op.Add -> bb.createAdd(lhsVal, rhsVal)
                    Op.Sub -> bb.createSub(lhsVal, rhsVal)
                    Op.Mul -> bb.createMul(lhsVal, rhsVal)
                    Op.Div -> bb.createDiv(lhsVal, rhsVal)
                    Op.Gt -> bb.addInst(CmpInst(CmpInstKind.ICMP_SGT, lhsVal, rhsVal))
                    Op.Ne -> bb.addInst(CmpInst(CmpInstKind.ICMP_NE, lhsVal, rhsVal))
                    Op.Eq -> bb.addInst(CmpInst(CmpInstKind.ICMP_EQ, lhsVal, rhsVal))
                    else -> {
                        println("not yet implemented {$op}")
                        unreachable()
                    }
                }
            }
            is IdentExpr -> {
                val decl = expr.identUse.decl
                val slot = decl2slot[decl]!!
                if(decl is FnDecl){
                    slot
                }else{
                    bb.createLoad(slot)
                }
            }
            is BlockExpr -> {
                var result : LLVMValue = LLVMNothing
                for( stmt in expr.stmts){
                    result = emitStmt(stmt)
                }
                result
            }
            is FnCallExpr -> {
                val callee = remitExpr(expr.callee)
                val args = mutableListOf<LLVMValue>()
                for( arg in expr.args){
                    val argDef = remitExpr(arg)
                    args.add(argDef)
                }
                bb.createCall(callee, args)
            }
            is RetExpr -> {
                val retExpr = expr.expr?.let {
                    remitExpr(it)
                }

                bb.createRet(retExpr)
                LLVMNothing
            }
            is IfExpr -> {
                remitIfExpr(expr)
            }
            /*
            is WhileExpr -> {
                remitWhileExpr(expr)
            }*/
            else -> {
                println("{$expr}")
                unreachable()
            }
        }
    }

    fun remitIfExpr(expr: IfExpr) : LLVMValue {
        val condition = remitExpr(expr.condition)

        val trueBB = fn.createBasicBlock("if-true")
        val endBB = fn.createBasicBlock("if-end")

        val falseBranch = expr.falseBranch

        val falseBB = if(falseBranch != null){
            fn.createBasicBlock("if-false")
        }else endBB

        bb.createCondBr(condition, trueBB, falseBB)
        bb = trueBB

        val leftVal = remitExpr(expr.trueBranch)
        bb.createBr(endBB)

        if( falseBranch != null ){
            val rightVal = remitExpr(falseBranch)
            bb.createBr(endBB)
        }

        bb = endBB
        return LLVMNothing
    }
/*
    fun remitWhileExpr(expr: WhileExpr) : Def {
        val tyUnit = b.tyUnit()
        val unit =  b.unit()
        val bot =  b.bot()

        val whileTy = b.pi(unit, bot)

        val bodyFun = b.lam(whileTy)
        val exitFun = b.lam(whileTy)

        val cmp = remitExpr(expr.condition)
        val branches = b.tuple(arrayOf(bodyFun, exitFun))
        val callee = b.extract(branches, cmp)

        val app = b.app(callee, unit)
        jump_bb(app, bodyFun)

        remitExpr(expr.body)

        val cmp2 = remitExpr(expr.condition)
        val branches2 = b.tuple(arrayOf(bodyFun, exitFun))
        val callee2 = b.extract(branches2, cmp2)

        jump_bb(callee2, exitFun)
        return unit
    }*/
}
enum class HirEmitterMode{
    Decl,
    Def
}

class HirEmitter(val symTable : SymTable){
    val fns = HashMap<String, Def>()
    val structs = HashMap<String, Def>()
    val struct2def = HashMap<Ty, Def>()
    val decl2def = HashMap<Decl, Def>()

    val world = World()
    val b = Builder(world)
    var currentMem: Def? = null
    var currentBB: Def? = null
    var mode : HirEmitterMode = HirEmitterMode.Decl

    fun getFun(name : String) : Def{
        val def = fns[name]!!
        return b.constructor().construct(def)
    }

    fun mapDecl(decl: Decl, value: Def){
        val name = decl.ident.name
        when( decl ){
            is FnDecl -> fns[name] = value
            is StructDecl -> structs[name] = value
        }

        decl2def[decl] = value
    }

    fun getDecl(decl: Decl) : Def?{
        return decl2def[decl]
    }

    fun emitOptTy(ty: Ty? ) : Def{
        return when(ty){
            null -> b.tyUnit()
            else -> emitTy(ty)
        }
    }

    fun emitTy(ty: Ty ) : Def{
        return when(ty){
            is PrimTy -> {
                when(ty.kind){
                    PrimTyKind.I32 -> b.tyInt(32)
                    PrimTyKind.I64 -> b.tyInt(64)
                    PrimTyKind.F32 -> b.tyReal(32)
                    PrimTyKind.F64 -> b.tyReal(64)
                    else -> unreachable()
                }
            }
            is FnTy -> {
                val arr = Array<Def>(ty.params.size){world.empty}
                for( (idx, param) in ty.params.withIndex() ){
                    arr[idx] = emitTy(param)
                }
        
                val argTy = b.sigma(arr)
                val retTy = emitOptTy(ty.ret_ty)
        
                b.pi(argTy, retTy)
            }
            is StructTy -> {
                val testTy = struct2def[ty]
                if( testTy != null ){
                    testTy
                }else{
                    val memberTys = Array<Def>(ty.members.size){world.empty}

                    val sigma = b.sigma(memberTys)
                    struct2def[ty] = sigma
            
                    for( (idx, member_ty) in ty.members.withIndex()){
                        val memTyDef = emitTy(member_ty)
                        sigma.setOp(idx, memTyDef)
                    }

                    sigma
                }
            }
            else -> {
                println("{$ty}")
                unreachable()
            }
        }
    }

    fun handleMemResult(def : Def) : Def{
        val zero = b.litIdx(2, 0)
        val one = b.litIdx(2, 1)
        val mem = b.extract(def, zero)
        val res = b.extract(def, one)
        currentMem = mem
        return res
    }

    fun mem() : Def {
        val mem = currentMem
        return when(mem){
            null -> unreachable()
            else -> mem
        }
    }

    fun emitModule(module: Module){
        mode = HirEmitterMode.Decl
        for( item in module.items){
            emitDecl(item)
        }
        
        mode = HirEmitterMode.Def
        for( item in module.items){
            emitDecl(item)
        }
    }

    fun emitDecl(decl: Decl){
        if( mode == HirEmitterMode.Decl){
            when(decl){
                is FnDecl -> {
                    val fncTy = emitTy(decl.ty!!)
                    val fnc = b.lam(fncTy)
                    mapDecl(decl, fnc)
                }
                is StructDecl -> {
                    val structTy = decl.ty
                    if( structTy != null ){
                        val structTyDef = emitTy(structTy)
                        mapDecl(decl, structTyDef)
                    }else{
                        b.bot()
                    }
                }
                else -> {}
            }
        }else{
            when(decl){
                is FnDecl -> {
                    val test = getDecl(decl);
                    val fnc = if( test != null ){
                        test
                    }else{
                        val fncTy = emitTy(decl.ty!!)
                        val fnc = b.lam(fncTy)
                        fnc.dbg = decl.ident.name
                        mapDecl(decl, fnc)
                        fnc
                    }
    
                    val `var` = b.`var`(fnc)
                    val arity = b.tyIdx(decl.params.size)
    
                    for( (idx, param) in decl.params.withIndex()){
                        val pos = b.lit(idx, arity)
                        val value = b.extract(`var`, pos)
                        value.dbg = param.ident.name
                        mapDecl(param, value)
                    }
    
                    currentBB = fnc
                    val body = remitExpr(decl.body)
                    val ret = b.ret(fnc)
                    val retApp = b.app(ret, body)
                    b.setBody(currentBB!!, retApp)
                }
                else -> {}
            }
        }
    }

    fun emitStmt(stmt: Stmt) : Def {
        return when(stmt){
            is ExprStmt -> {
                remitExpr(stmt.expr)
            }
            is LetStmt ->{
                //val ty = emit_ty(let_stmt.local_decl.ty)

                val init = stmt.init
                if( init != null ){
                    val initVal = remitExpr(init)
                    mapDecl(stmt.localDecl, initVal)
                }else{
                    unreachable<Def>()
                }
    
                b.unit()
            }
            else -> unreachable()
        }
    }

    fun remitExpr(expr: Expr) : Def {
        return when(expr){
            is LiteralExpr -> {
                when(val literal = expr.literal){
                    is IntLiteral -> b.litInt(32, literal.value)
                    else -> unreachable()
                }
            }
            is InfixExpr -> {
                //val ty = emit_ty(expr.ty)
                val lhsVal = remitExpr(expr.lhs)
                val rhsVal = remitExpr(expr.rhs)

                when(val op = expr.op){
                    Op.Add -> b.add(lhsVal, rhsVal)
                    Op.Sub -> b.sub(lhsVal, rhsVal)
                    Op.Mul -> b.mul(lhsVal, rhsVal)
                    Op.Div -> b.div(lhsVal, rhsVal)
                    Op.Gt -> b.gt(lhsVal, rhsVal)
                    Op.Ne -> b.ne(lhsVal, rhsVal)
                    else -> {
                        println("not yet implemented {$op}")
                        unreachable()
                    }
                }
            }
            is IdentExpr -> {
                val decl = expr.identUse.decl
                decl2def[decl]!!
            }
            is BlockExpr -> {
                var result = b.bot()
                for( stmt in expr.stmts){
                    result = emitStmt(stmt)
                }
                result
            }
            is FnCallExpr -> {
                val callee = remitExpr(expr.callee)
        
                val arr = Array<Def>(expr.args.size){world.empty}
                for( (idx, arg) in expr.args.withIndex()){
                    val argDef = remitExpr(arg)
                    arr[idx] = argDef
                }
        
                val argDef = b.tuple(arr)
                b.app(callee, argDef)
            }
            is RetExpr -> {
                val fnc = getDecl(expr.decl!!)
                val retVal = remitExpr(expr.expr!!)
                val ret = b.ret(fnc!!)
                val retApp = b.app(ret, retVal)
                finish_bb(retApp)
                b.nothing()
            }
            is IfExpr -> {
                remitIfExpr(expr)
            }
            is WhileExpr -> {
                remitWhileExpr(expr)
            }
            else -> {
                println("{$expr}")
                unreachable()
            }
        }
    }

    fun remitIfExpr(expr: IfExpr) : Def{
        val tyUnit = b.tyUnit()
        val unit =  b.unit()
        val bot =  b.bot()

        val ifTy = b.pi(unit, bot)

        val trueFun = b.lam(ifTy)
        val falseFun = b.lam(ifTy)

        val cmp = remitExpr(expr.condition)
        val branches = b.tuple(arrayOf(trueFun, falseFun))
        val callee = b.extract(branches, cmp)

        val app = b.app(callee, unit)
        jump_bb(app, trueFun)

        val leftVal = remitExpr(expr.trueBranch)

        val falseBranch = expr.falseBranch
        return if( falseBranch != null ){
            val valTy = emitOptTy(expr.ty)
            val joinTy = b.pi(valTy, bot)
            val joinFun = b.lam(joinTy)

            val appLeft = b.app(joinFun, leftVal)
            jump_bb(appLeft, falseFun)

            val rightVal = remitExpr(falseBranch)
            val appRight = b.app(joinFun, rightVal)
            jump_bb(appRight, joinFun)

            val joinVar = b.`var`(joinFun)
            joinVar
        }else{
            val app = b.app(falseFun, unit)
            jump_bb(app, falseFun)
            unit
        }
    }

    fun remitWhileExpr(expr: WhileExpr) : Def{
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
    }

    fun jump_bb(body: Def, next: Def){
        b.setBody(currentBB!!, body)
        currentBB = next
    }

    fun finish_bb(body: Def){
        val tyUnit = b.tyUnit()
        val bot = b.bot()
        val tyUnreachable = b.pi(tyUnit, bot)
        val unreachable = b.lam(tyUnreachable)
        jump_bb(body, unreachable)
    }

    fun list(){
        val constructor = b.constructor()


        if(true){
            val world = World()
            val ax = world.axiom(Axiom.TyReal)
            val builder = Builder(world)
            val i32 = builder.tyInt(32)
            val i64 = builder.tyInt(64)
            val a = NodeDef(ax, arrayOf(world.empty, i32))
            val b = NodeDef(ax, arrayOf(world.empty, i64))
            a.ops[0] = b
            b.ops[0] = a
            a.dbg = "a"
            b.dbg = "b"
            val aNew = constructor.construct(a)
            val bNew = constructor.construct(b)

            println("test")
        }

        println("-------------------------------------------------")
        println("Structs:")
        for( (name, def) in structs){
            val new = constructor.construct(def)
            val signature = "%X".format(new.hash)
            println("    $name $signature")
        }
        println("Functions:")
        val constFns = arrayListOf<Def>()
        for( (name, def) in fns){
            val new = constructor.construct(def)
            constFns.add(new)
            val signature = "%X".format(new.hash)
            println("    $name $signature")
        }
        println("-------------------------------------------------")
    }

    fun emit() : List<Def>{
        var res = ArrayList<Def>()

        val constructor = b.constructor()
        for( (_, def) in fns){
            res.add(constructor.construct(def))
        }

        return res
    }
}

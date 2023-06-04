
class TyTable {
    val primTys = HashMap<PrimTyKind, Ty>()
    val declTys = HashMap<Decl, Ty>()

    fun primTy(ty: PrimTyKind) : Ty {
        return primTys.computeIfAbsent(ty){PrimTy(it)}
    }

    fun structTy(ty: StructASTTy) : Ty {
        val decl = ty.identUse.decl ?: return unreachable()
        return declTy(decl)!!
    }

    fun inferOrUnit(ty: ASTTy?) : Ty{
        return if( ty != null ){
            infer(ty)
        }else{
            primTy(PrimTyKind.Unit)
        }
    }

    fun infer(astTy: ASTTy) : Ty {
        return when( astTy ){
            is PrimASTTy -> primTy(astTy.kind)
            is StructASTTy -> structTy(astTy)
            else -> unreachable()
        }
    }

    fun declTy(decl: Decl) : Ty? {
        val ty = decl.ty
        if( ty != null ){
            return ty
        }

        val result = when( decl ){
            is StructDecl -> {
                val members = ArrayList<Ty>()
                val structTy = StructTy( decl.ident.sym, members )
                decl.ty = structTy
    
                for( member in decl.members ){
                    val memberTy = declTy(member)
                    members.add(memberTy!!)
                }
    
                declTys[decl] = structTy
                decl.ty
            }
            is MemberDecl -> {
                infer(decl.astTy)
            }
            is LocalDecl -> {
                val ast_ty = decl.ast_ty
                if( ast_ty != null ){
                    val result = infer(ast_ty)
                    result
                }else{
                    null
                }
            }
            is FnDecl -> {
                val paramTys = ArrayList<Ty>()
                for( param in decl.params){
                    declTy(param)
                    paramTys.add(param.ty!!)
                }
        
                val retTy = inferOrUnit(decl.ret_ty)
    
                val funTy = FnTy(paramTys, retTy)
                decl.body.ty = retTy
                declTys[decl] = funTy
                funTy
            }
            else -> null

        }
        decl.ty = result
        return result
    }
}

class TypeChecker(val tyTable: TyTable) {

    fun coerceOption(expr: Expr, ty: Ty?){
        if( ty != null ){
            coerce(expr, ty)
        }
    }

    fun coerceOptionDecl(decl: Decl, ty: Ty?){
        if( ty != null ){
            coerce_decl(decl, ty)
        }
    }

    fun coerce(expr: Expr, ty: Ty){
        val exprTy = expr.ty
        if( exprTy != null ){
            if( exprTy != ty ){
                expr.ty = ErrTy
            }
        }else{
            expr.ty = ty
        }
    }

    fun coerce_decl(decl: Decl, ty: Ty){
        val declTy = decl.ty
        if( declTy != null ){
            if( declTy != ty ){
                decl.ty = ErrTy
            }
        }else{
            decl.ty = ty
        }
    }

    fun primTy(prim_ty: PrimTyKind) : Ty{
        return tyTable.primTy(prim_ty)
    }

    fun errTy() : Ty{
        return ErrTy
    }

    fun infer(ast_ty: ASTTy) : Ty{
        return tyTable.infer(ast_ty)
    }

    fun declTy(decl: Decl) : Ty?{
        return tyTable.declTy(decl)
    }

    fun joinTy(lhs: Ty, rhs: Ty) : Ty{
        return if( lhs != rhs){
            errTy()
        }else{
            lhs
        }
    }

    fun joinOptionTy(lhs: Ty?, rhs: Ty?) : Ty?{
        return if( lhs != null ){
            if( rhs != null ){
                joinTy(lhs, rhs)
            }else{
                lhs
            }
        }else rhs
    }

    fun enterStmt(stmt: Stmt) {
        when(stmt){
            is LetStmt -> {
                val local_decl = stmt.localDecl
                val ast_ty = local_decl.ast_ty
            
                if( ast_ty != null ){
                    val ty = infer(ast_ty)
                    coerce_decl(local_decl, ty)
                }

                val init = stmt.init
                if( init != null ){
                    coerceOption(init, local_decl.ty)
                    coerceOptionDecl(local_decl, init.ty)
                }
            }
            is ExprStmt ->{
                visitExpr(stmt.expr)
            }
            else -> {}
        }
    }

    fun visitDecl(decl: Decl) {
        when(decl){
            is MemberDecl -> {
                declTy(decl)
            }
            is StructDecl -> {
                declTy(decl)
        
                for( member in decl.members){
                    visitDecl(member)
                }
            }
            is FnDecl -> {
                for( param in decl.params){
                    visitDecl(param)
                }

                declTy(decl)

                visitExpr(decl.body)
            }
            else -> {}
        }
    }

    fun visitExpr(expr: Expr) {
        when( expr ){  
            is InfixExpr -> {
                when( expr.op ){
                    Op.And , Op.Or -> {
                        val bool = primTy(PrimTyKind.Bool)
                        coerce(expr.lhs, bool)
                        coerce(expr.rhs, bool)
                        coerce(expr, bool)
                    }
                    Op.Eq , Op.Ne -> {
                        coerce(expr, primTy(PrimTyKind.Bool))
                    }
                    Op.Add , Op.Sub , Op.Mul , Op.Div -> {
        
                        var join_option_ty = joinOptionTy(expr.ty, expr.lhs.ty )
                        join_option_ty = joinOptionTy(join_option_ty, expr.rhs.ty)
    
                        if( join_option_ty != null ){
                            coerce(expr.lhs, join_option_ty)
                            coerce(expr.rhs, join_option_ty)
                        }
            
                        visitExpr(expr.lhs)
                        visitExpr(expr.rhs)
            
                        if( join_option_ty != null ){
                            coerce(expr, join_option_ty)
                        }
                    }
                    else -> return
                }
            }
            is LiteralExpr -> {
                when( expr.literal ){
                    is StrLiteral -> coerce(expr, primTy(PrimTyKind.Str))
                    is BoolLiteral -> coerce(expr, primTy(PrimTyKind.Bool))
                    is CharLiteral -> coerce(expr, primTy(PrimTyKind.Char))
                    else -> {}
                }
            }
            is IdentExpr -> {
                val ty = expr.ty
                if( ty != null ){
                    val decl = expr.identUse.decl
                    if( decl != null ){
                        coerce_decl(decl, ty)
                    }
                }
            }
            is BlockExpr -> {
                val retTy = expr.ty
                if( retTy != null ){
                    val lastStmt = expr.stmts.lastOrNull()
        
                    if( lastStmt != null && lastStmt is ExprStmt ){
                        coerce(lastStmt.expr, retTy)
                    }
        
                    for( stmt in expr.stmts.reversed()){
                        visitStmt(stmt)
                    }
                }
            }
            else -> return
        }
    }

    fun visitStmt(stmt: Stmt){
        enterStmt(stmt)
        when( stmt ){
            is ExprStmt -> visitExpr(stmt.expr)
            is LetStmt -> {
                val init = stmt.init
                if(init != null){
                    visitExpr(init)
                }

                visitDecl(stmt.localDecl)
            }
        }
        enterStmt(stmt)
    }

    fun check(module: Module){
        for( item in module.items){
            visitDecl(item)
        }
    }
}
import java.util.*
import kotlin.collections.HashMap

enum class BindMode {
    Discover,
    Binding
}

class Binder {
    val levels = LinkedList<Int>()
    val decls = LinkedList<Decl>()
    val symbol2decl = HashMap<Sym, Decl>()
    lateinit var currFunDecl : FnDecl
    var mode: BindMode = BindMode.Discover
    
    fun pushScope() {
        levels.add(decls.size)
    }

    fun popScope() {
        val last_level = levels.last()

        for( x in last_level until decls.size ){
            val lastDecl = decls.pop()

            val shadows = lastDecl.shadows
            if( shadows != null ){
                symbol2decl[lastDecl.ident.sym] = shadows
            }
        }

        levels.pop()
    }

    fun lookup(sym: Sym) : Decl? {
        return symbol2decl[sym]
    }

    fun findLocal(sym: Sym) : Decl? {
        val other = lookup(sym)
        if( other != null ){
            if( other.depth == levels.size ){
                return other
            }
        }

        return null
    }

    fun insert(decl: Decl) {
        val sym = decl.ident.sym

        decl.depth = levels.size
        decl.shadows = lookup(sym)

        /*
        if val Some(other) = find_local(sym) {
            unreachable!()
        }*/

        symbol2decl[sym] = decl
        decls.push(decl)
    }

    fun bind(module: Module) {
        visit_module(module)
    }
    
    fun enter_expr(expr: Expr) {
        when( expr ){
            is BlockExpr -> pushScope()
            is IdentExpr -> {
                val identUse = expr.identUse
        
                val decl = lookup(identUse.ident.sym)
                if(decl != null){
                    identUse.decl = decl
                }else{
                    println("count not bind {$expr}")
                }
            }
            is RetExpr -> {
                expr.decl = currFunDecl
            }
            else -> {}
        }
    }

    fun exit_expr(expr: Expr) {
        if(expr is BlockExpr){
            popScope()
        }
    }

    fun visit_module(module: Module){
        mode = BindMode.Discover
        for( item in module.items){
            visit_decl(item)
        }
        mode = BindMode.Binding
        for( item in module.items ){
            visit_decl(item)
        }
    }

    fun visit_decl(decl: Decl){
        if( mode == BindMode.Discover ){
            insert(decl)
        }else{
            when( decl ){
                is FnDecl -> {
                    pushScope()
                    for( param in decl.params ){
                        visit_decl(param)
                    }
                    currFunDecl = decl
                    visit_expr(decl.body)
                    popScope()
                }
                is StructDecl -> {
                    for( member in decl.members){
                        visit_decl(member)
                    }
                }
                is MemberDecl -> {
                    var astType : ASTTy = decl.astTy
                    if( astType is StructASTTy ){
                        val decl = lookup(astType.identUse.ident.sym)
                        astType.identUse.decl = decl
                    }
                }
                is LocalDecl -> insert(decl)
                else -> {}
            }
        }
    }

    fun visit_expr(expr: Expr){
        enter_expr(expr)
        when(expr){
            is InfixExpr -> {
                visit_expr(expr.lhs)
                visit_expr(expr.rhs)
            }
            is PostfixExpr -> {
                visit_expr(expr.expr)
            }
            is PrefixExpr -> {
                visit_expr(expr.expr)
            }
            is RetExpr -> {
                val expr = expr.expr
                if(expr != null){
                    visit_expr(expr)
                }
            }
            is IdentExpr -> {
                //visit_ident_expr( ident)
            }
            is BlockExpr -> {
                for( stmt in expr.stmts){
                    visit_stmt(stmt)
                }
            }
            is FnCallExpr -> {
                visit_expr(expr.callee)
                for( arg in expr.args){
                    visit_expr(arg)
                }
            }
            is IfExpr -> {
                visit_expr(expr.condition)
                visit_expr(expr.trueBranch)
                val falseBranch = expr.falseBranch
                if(falseBranch != null){
                    visit_expr(falseBranch)
                }
            }
            is WhileExpr -> {
                visit_expr(expr.condition)
                visit_expr(expr.body)
                val elseBranch = expr.elseBranch
                if(elseBranch != null){
                    visit_expr(elseBranch)
                }
            }
            else -> return
        }
        exit_expr(expr)
    }

    fun visit_stmt(stmt: Stmt){
        when( stmt ){
            is ExprStmt -> visit_expr(stmt.expr)
            is LetStmt -> {
                val init = stmt.init;
                if(init != null){
                    visit_expr(init);
                }
    
                visit_decl(stmt.localDecl);
            }
        }
    }
}
package frontend

class Module(var items: List<Decl>)

open class Decl(var ident: Ident){
    var ty: Ty? = null
    var shadows: Decl? = null
    var depth: Int = 0
}
class LocalDecl(
    ident: Ident,
    val ast_ty: ASTTy?) : Decl(ident);

class FnDecl(
    ident: Ident,
    val params: List<Decl>,
    val ret_ty: ASTTy?,
    val body: Expr
) : Decl(ident)

class MemberDecl(
    ident: Ident,
    val astTy: ASTTy,
    val index: Int) : Decl(ident);

class StructDecl(
    ident: Ident,
    val members: List<Decl>,
) : Decl(ident);


class Ident(val sym: Sym){
    val name get() = sym.value
}


interface ASTTy

class PrimASTTy(val kind: PrimTyKind) : ASTTy
class StructASTTy(val identUse: IdentUse) : ASTTy
class FnASTTy(val param_types: List<ASTTy>,
              val return_type: ASTTy
) : ASTTy
object ErrASTTy : ASTTy


enum class PrimTyKind{
    Str,
    Unit,
    Byte,
    Char,
    I32,
    I64,
    F32,
    F64,
    Bool
}


interface Stmt
class ExprStmt(val expr: Expr) : Stmt
class LetStmt(val localDecl: LocalDecl, val init : Expr?) : Stmt

open class Expr {
    var ty: Ty? = null
}

class LiteralExpr(val literal : Literal) : Expr()

class BlockExpr(val stmts: List<Stmt>) : Expr()

class IdentExpr(val identUse: IdentUse) : Expr()

class FnCallExpr(val callee : Expr,
                 val args : List<Expr>) : Expr()

class FieldExpr(val target: Expr,
                val identifier: IdentUse?,
                val index: Int?) : Expr()

class IfExpr(val condition : Expr,
             val trueBranch: Expr,
             val falseBranch: Expr?) : Expr()

class WhileExpr(
    val condition : Expr,
    val body : Expr,
    val elseBranch : Expr?) : Expr()

class PrefixExpr(val expr: Expr,
                 val op: Op
) : Expr()
class InfixExpr(
    val lhs: Expr,
    val rhs: Expr,
    val op: Op
) : Expr()

class PostfixExpr(val expr: Expr,
                  val op: Op
) : Expr()
class RetExpr(
    val expr: Expr?) : Expr(){
    var decl: Decl? = null
}


class IdentUse(val ident: Ident) {
    var decl: Decl? = null
}

sealed interface Literal
class StrLiteral(val value: String) : Literal
class CharLiteral(val value: Char) : Literal
class IntLiteral(val value: Int) : Literal
class RealLiteral(val value: Double) : Literal
class BoolLiteral(val value: Boolean) : Literal


enum class Op {
    Assign,
    AddAssign,
    SubAssign,
    MulAssign,
    DivAssign,

    Or,
    And,
    Eq,
    Ne,
    BitOr,
    BitXor,
    BitAnd,
    Shl,
    Shr,
    Add,
    Sub,

    Inc,
    Dec,
    Not,
    Lt,
    Le,
    Gt,
    Ge,
    Mul,
    Div,
    Rem,
    Arrow,
    Dot,
    LeftParen,
    LeftBracket;
    
    fun isPostfix() : Boolean {
        return when(this){
            Inc ,
            Dec ,
            LeftBracket ,
            LeftParen ,
            Dot -> true
            else -> false
        }
    }

    fun isPrefix() : Boolean {
        return when(this){
            Add ,
            Sub ,
            Inc ,
            Dec ,
            Not -> true
            else -> false
        };
    }

    fun isInfix() : Boolean {
        return when(this){
            Or ,
            And ,
            Assign ,
            Eq ,
            Ne ,
            Lt ,
            Le ,
            Gt ,
            Ge ,
            Add ,
            Sub ,
            Mul ,
            Div -> true
            else -> false
        }
    }

    fun prec() : Prec {
        return when(this){
            Or -> Prec.Or
            And -> Prec.And
            Eq , Ne -> Prec.Rel
            BitOr -> Prec.BitOr
            BitXor -> Prec.BitXor
            BitAnd -> Prec.Add
            Shl , Shr -> Prec.Shift
            Add , Sub -> Prec.Add
            Mul , Div , Rem -> Prec.Mul
            else -> Prec.Bottom
        }
    }
    
    fun sign() : String {
        return when(this){
            Or -> "|"
            And -> "&"
            Eq -> "=="
            Ne -> "!="
            Lt -> "<"
            Le -> "<="
            Gt -> ">"
            Ge -> ">="
            Add -> "+"
            Sub -> "-"
            Mul -> "*"
            Div -> "/"
            Inc -> "++"
            Dec -> "--"
            Assign -> "="
            AddAssign -> "+="
            SubAssign -> "-="
            MulAssign -> "*="
            DivAssign -> "/="
            else -> "?"
        }
    }
}




enum class Prec {
    Bottom,
    Assign,
    Or,
    And,
    Rel,
    BitOr,
    BitXor,
    BitAnd,
    Shift,
    Add,
    Mul,
    As,
    Unary,
    Top;
    
    fun next() : Prec {
        return when(this){
            Bottom -> Assign
            Assign -> Or
            Or -> And
            And -> BitOr
            BitOr -> BitXor
            BitXor -> BitAnd
            BitAnd -> Shift
            Shift -> Add
            Add -> Mul
            Mul -> As
            As -> Unary
            Unary -> Top
            else -> Top
        }
    }
}
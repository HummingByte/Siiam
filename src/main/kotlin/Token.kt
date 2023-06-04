class Token(var kind: TokenKind,
            var enter: TokenEnter,
            var symbol: Sym?,
            var loc: Loc){


    companion object{
        private val ERROR = Token(TokenKind.Error,
            TokenEnter.NL,
        null,
            Loc.zero())

        fun error() : Token{
            return ERROR
        }
    }
}

enum class TokenKind {
    Or,
    And,
    Not,
    Plus,
    Minus,
    Star,
    Hat,
    Slash,
    Assign,

    LAngle, RAngle,
    LParen, RParen,
    LBracket, RBracket,
    LBrace, RBrace,
    Comma, Semicolon, Colon, Arrow,
    Dot,
    While, For, If, Else, Continue,
    Return,
    Let, Fn, Struct,

    LitStr, LitChar, LitInt, LitReal, LitBool,
    TypeStr, TypeUnit, TypeByte, TypeChar, TypeI32, TypeI64, TypeF32, TypeF64, TypeBool,

    NL,
    Error,
    Eof,
    Num,
    Ident;


    fun keyword() : String{
        return when(this) {
            While ->  "while"
            For ->  "for"
            Continue ->  "continue"
            If ->  "if"
            Else ->  "else"
            Let ->  "let"
            Fn ->  "fn"
            Return ->  "return"
            TypeStr ->  "String"
            TypeChar ->  "char"
            TypeBool ->  "bool"
            TypeByte ->  "i8"
            TypeI32 ->  "i32"
            TypeI64 ->  "i64"
            TypeF32 ->  "f32"
            TypeF64 ->  "f64"
            TypeUnit ->  "Unit"
            Struct ->  "struct"
            else ->  ""
        }
    }
}

enum class TokenEnter {
    Token, Space, NL
}
package frontend

import kotlin.text.StringBuilder


fun range(c: Char, low: Char, high: Char) : Boolean { return low <= c && c <= high; }
fun sym(c : Char) : Boolean { return  range(c, 'a', 'z') || range(c, 'A', 'Z') || c == '_'; }
fun space(c : Char) : Boolean { return c == ' ' || c == '\t' || c == '\n'; }
fun dec_nonzero(c : Char) : Boolean { return range(c, '1', '9'); }
fun bin(c : Char) : Boolean { return range(c, '0', '1'); }
fun oct(c : Char) : Boolean { return range(c, '0', '7'); }
fun dec(c : Char) : Boolean { return range(c, '0', '9'); }
fun hex(c : Char) : Boolean { return dec(c) || range(c, 'a', 'f') || range(c, 'A', 'F'); }
fun eE(c : Char) : Boolean { return c == 'e' || c == 'E'; }
fun sgn(c : Char) : Boolean{ return c == '+' || c == '-'; } 

class Lexer(val source: Source, val symTable : SymTable) {
    var token: Token = Token(TokenKind.Error, TokenEnter.NL, null, Span.zero())
    var enter: TokenEnter = TokenEnter.NL
    val builder = StringBuilder()

    fun next() : Char? {
        return source.next()
    }

    fun ident() : Boolean{
        if(acceptString(::sym)){
            while( acceptString(::sym) || acceptString(::dec)){}
            return true
        }

        return false
    }

    fun eof() : Boolean{
        return source.peek() == null
    }

    fun finish(kind: TokenKind) {
        token.kind = kind
        token.symbol = null
    }

    fun finish_ident(str: String) {
        for(kind in TokenKind.values()){
            if(kind.keyword() == str){
                return finish(kind);
            }
        }

        finish(TokenKind.Ident, str)
    }

    fun finish(kind: TokenKind, str: String) {
        token.kind = kind;
        token.symbol = symTable.from(str)
    }

    fun accept(expect : Char) : Boolean{
        return source.accept { actual -> actual == expect }
    }

    fun accept(pred: (Char) -> Boolean) : Boolean{
        return source.accept(pred)
    }

    fun acceptString() : Boolean{
        return source.acceptString(builder)
    }

    fun acceptString(expect : Char) : Boolean{
        return acceptString { actual -> actual == expect}
    }

    fun acceptString(pred: (Char) -> Boolean) : Boolean{
        return source.acceptString(builder, pred)
    }

    fun nextToken() : Token {
        nextTokenImpl()
        token.span = source.loc()
        token.enter = enter
        enter = TokenEnter.Token
        return Token(token.kind, token.enter, token.symbol, token.span)
    }

    private fun lexSign() : TokenKind?{
        val ch = source.peek();
        return if( ch != null ){
            val result = when(ch) {
                '+' -> TokenKind.Plus
                '-' -> TokenKind.Minus
                '*' -> TokenKind.Star
                '^' -> TokenKind.Hat
                '=' -> TokenKind.Assign
                '!' -> TokenKind.Not
                ',' -> TokenKind.Comma
                '.' -> TokenKind.Dot
                ':' -> TokenKind.Colon
                ';' -> TokenKind.Semicolon
                '|' -> TokenKind.Or
                '&' -> TokenKind.And
                '<' -> TokenKind.LAngle
                '>' -> TokenKind.RAngle
                '(' -> TokenKind.LParen
                ')' -> TokenKind.RParen
                '[' -> TokenKind.LBracket
                ']' -> TokenKind.RBracket
                '{' -> TokenKind.LBrace
                '}' -> TokenKind.RBrace
                else -> return null
            }
            next()
            result
        }else{
            null
        }
    }

    private fun nextTokenImpl() {
        while(true) {
            source.resetLoc()

            if( eof() ){
                return finish(TokenKind.Eof)
            }

            if(accept(::space)){
                while(accept(::space)){}

                if( source.last.line != source.current.line ){
                    enter = TokenEnter.NL
                }else if( enter == TokenEnter.Token){
                    enter = TokenEnter.Space
                }

                continue
            }

            if(accept('/')){
                if(accept('*')){ // arbitrary comment
                    var depth = 1
                    while(true) {
                        if( eof() ){
                            return finish(TokenKind.Error)
                        }

                        if(accept('/')){
                            if(accept('*')){
                                depth += 1
                            }
                        } else if(accept('*')){
                            if(accept('/')){
                                depth -= 1
                                if( depth == 0 ){
                                    break
                                }
                            }

                            continue
                        }

                        next()
                    }
                    continue
                }
                if(accept('/')){
                    while(true) {
                        if( eof() ){
                            return finish(TokenKind.Error)
                        }

                        if(accept('\n')){
                            break
                        } else {
                            next()
                        }
                    }
                    continue;
                }
                return finish(TokenKind.Slash)
            }

            val token = lexSign()
            if( token != null ){
                return finish(token)
            }

            builder.setLength(0)

            // identifiers/keywords
            if( ident() ){
                val str = builder.toString()
                if( str == "true" || str == "false" ){
                    return finish(TokenKind.LitBool, str)
                }
                return finish_ident(str)
            }

            // Char literal
            if(accept('\'')){
                if(accept('\'')){
                    return finish(TokenKind.Error);
                }

                this.acceptString('\\');
                this.acceptString()

                if( eof() || !accept('\'')){
                    return finish(TokenKind.Error)
                }

                val str = builder.toString()
                return finish(TokenKind.LitChar, str)
            }

            // string literal
            if(accept('"')){
                while( !accept('"')){
                    this.acceptString('\\')
                    this.acceptString();

                    if( eof() ){
                        return finish(TokenKind.Error)
                    }
                }

                val str = builder.toString()
                return finish(TokenKind.LitStr, str)
            }

            if( this.acceptString(::dec) ){
                while( this.acceptString(::dec) ){
                    if( eof() ){
                        return finish(TokenKind.Error)
                    }
                }

                return if( this.acceptString('.') ){
                    while( this.acceptString(::dec) ){
                        if( eof() ){
                            return finish(TokenKind.Error)
                        }
                    }

                    val str = builder.toString()
                    finish(TokenKind.LitReal, str)
                } else {
                    val str = builder.toString()
                    finish(TokenKind.LitInt, str)
                }
            }

            return finish(TokenKind.Error);
        }
    }
}

fun <T> unreachable() : T{
    throw RuntimeException()
}

class Parser(val lexer: Lexer, val symTable : SymTable) {
    companion object{
        val LOOKAHEAD_SIZE : Int = 3
    }

    val ahead = Array(LOOKAHEAD_SIZE){ lexer.nextToken() }
    var ahead_offset : Int = 0
    var last_op: Op? = null

    fun shift() {
        val next = lexer.nextToken()
        ahead[ahead_offset] = next
        ahead_offset = (ahead_offset + 1) % LOOKAHEAD_SIZE;
    }

    private fun lex() : Token {
        val result = ahead()
        shift()
        return result
    }

    fun kind() : TokenKind {
        return ahead().kind
    }

    private fun enter(enter: TokenEnter) : Boolean {
        return ahead().enter == enter
    }

    private fun ahead() : Token {
        return ahead_at(0)
    }

    private fun ahead_at(i: Int) : Token {
        return ahead[(i + ahead_offset) % LOOKAHEAD_SIZE]
    }

    private fun isKind(kind: TokenKind) : Boolean {
        return kind == kind()
    }

    private fun accept(kind: TokenKind) : Boolean {
        if( isKind(kind) ){
            shift()
            return true
        }

        return false;
    }

    private fun follow(kind: TokenKind) : Boolean {
        if( enter(TokenEnter.Token) ){
            return accept(kind)
        }

        return false
    }

    fun expect(kind: TokenKind) {
        assert(accept(kind)){"Kinds do not match!"}
    }

    private fun expectEnter(enter: TokenEnter) {
        assert(ahead().enter == enter){"Kinds do not match!"}
    }

    fun parseModule() : Module {
        val items = mutableListOf<Decl>()
        while( !accept(TokenKind.Eof) && !accept(TokenKind.RBrace) ){
            items.add(parseItem())
        }

        return Module(items)
    }

    fun parseItem() : Decl {
        return when( kind() ){
            TokenKind.Fn -> parseFun()
            TokenKind.Struct -> parseStruct()
            else -> unreachable()
        }
    }

    fun parseIdent() : Ident {
        assert(isKind(TokenKind.Ident))
        val sym = lex().symbol!!
        return Ident(sym)
    }

    fun parseStruct() : Decl {
        expect(TokenKind.Struct)
        val ident = parseIdent()
        expect(TokenKind.LBrace)
        val fields = parse_member_list()

        return StructDecl(ident, fields)
    }

    fun parseFun() : Decl {
        expect(TokenKind.Fn)
        val ident = parseIdent()
        expect(TokenKind.LParen)
        val params = parseParamList()
        var returnType : ASTTy? = null
        if( accept(TokenKind.Colon) ){
            returnType = parseType()
        }

        val body = parseBlock()
        return FnDecl(ident, params, returnType, body)
    }

    fun parseBlock() : Expr {
        expect(TokenKind.LBrace)
        val stmts = parse_statement_list()
        return BlockExpr(stmts)
    }

    fun parseReturn() : Expr {
        expect(TokenKind.Return)
        return RetExpr(parse_expr())
    }

    fun parse_member(i: Int) : Decl {
        val ident = parseIdent()
        accept(TokenKind.Colon)
        val ty = parseType();
        return MemberDecl(ident, ty, i)
    }

    fun prim_type() : PrimTyKind{
        val ty = when( kind() ){
            TokenKind.TypeBool -> PrimTyKind.Bool
            TokenKind.TypeByte -> PrimTyKind.Byte
            TokenKind.TypeChar -> PrimTyKind.Char
            TokenKind.TypeStr -> PrimTyKind.Str
            TokenKind.TypeI32 -> PrimTyKind.I32
            TokenKind.TypeI64 -> PrimTyKind.I64
            TokenKind.TypeF32 -> PrimTyKind.F32
            TokenKind.TypeF64 -> PrimTyKind.F64
            TokenKind.TypeUnit -> PrimTyKind.Unit
            else -> unreachable()
        };

        shift()
        return ty
    }

    fun parseType() : ASTTy {
        return when( kind() ){
            TokenKind.TypeBool ,
            TokenKind.TypeByte ,
            TokenKind.TypeChar ,
            TokenKind.TypeStr ,
            TokenKind.TypeI32 ,
            TokenKind.TypeI64 ,
            TokenKind.TypeF32 ,
            TokenKind.TypeF64 ,
            TokenKind.TypeUnit -> {
                PrimASTTy(prim_type())
            }
            TokenKind.Ident -> {
                val ident = parseIdent()
                StructASTTy(IdentUse(ident))
            }
            TokenKind.Fn -> {
                lex()
                accept(TokenKind.LParen)
                val paramTypes = parse_type_list()
                accept(TokenKind.Arrow)
                val returnType = parseType()
                FnASTTy(paramTypes, returnType)
            }
            else -> ErrASTTy
        }
    }

    fun parseOperator() : Op? {
        return if( last_op != null ){
            val tmp = last_op
            last_op  = null
            tmp
        }else if( accept(TokenKind.Plus) ){
            //+, ++, +=
            if( follow(TokenKind.Plus) ){
                Op.Inc
            } else if( follow(TokenKind.Assign) ){
                Op.Assign
            } else {
                Op.Add
            }
        } else if( accept(TokenKind.Minus) ){
            // -, --, -=, :
            if( follow(TokenKind.Minus) ){
                Op.Dec
            } else if( follow(TokenKind.RAngle) ){
                Op.Arrow
            } else if( follow(TokenKind.Assign) ){
                Op.SubAssign
            } else {
                Op.Sub
            }
        } else if( accept(TokenKind.Star) ){
            // *, *=
            if( follow(TokenKind.Assign) ){
                Op.MulAssign
            } else {
                Op.Mul
            }
        } else if( accept(TokenKind.Slash) ){
            // /, /=
            if( follow(TokenKind.Assign) ){
                Op.DivAssign
            } else {
                Op.Div
            }
        } else if( accept(TokenKind.LAngle) ){
            // <, <=, <<
            if( follow(TokenKind.Assign) ){
                Op.Le
            } else if( follow(TokenKind.LAngle) ){
                Op.Shl
            } else {
                Op.Lt
            }
        } else if( accept(TokenKind.RAngle) ){
            // >, >=, >>
            if( follow(TokenKind.Assign) ){
                Op.Ge
            } else if( follow(TokenKind.RAngle) ){
                Op.Shr
            } else {
                Op.Gt
            }
        } else if( accept(TokenKind.Assign) ){
            //=, ==
            if( follow(TokenKind.Assign) ){
                Op.Eq
            } else {
                Op.Assign
            }
        } else if( accept(TokenKind.Not) ){
            // !=, !
            if( follow(TokenKind.Assign) ){
                Op.Ne
            } else {
                Op.Not
            }
        } else if( accept(TokenKind.Or) ){
            //||, |
            if( follow(TokenKind.Or) ){
                Op.Or
            } else {
                Op.BitOr
            }
        } else if( accept(TokenKind.And) ){
            //&&, &
            if( follow(TokenKind.And) ){
                Op.And
            } else {
                Op.BitAnd
            }
        } else if( accept(TokenKind.LBracket) ){
            Op.LeftBracket
        } else if( accept(TokenKind.LParen) ){
            Op.LeftParen
        } else {
            return null
        }
    }

    fun parsePrefixExpr(op: Op) : Expr {
        return if( op == Op.LeftParen ){
            val expr = parse_expr();
            expect(TokenKind.RParen);
            expr
        }else{
            val expr = parse_expr_prec(op.prec())
            PrefixExpr(expr, op)
        }
    }

    fun parsePrimaryExpr() : Expr {
        return when( kind() ){
            TokenKind.LitInt,
            TokenKind.LitReal,
            TokenKind.LitStr,
            TokenKind.LitChar,
            TokenKind.LitBool -> LiteralExpr(parseLiteral())
            TokenKind.Ident -> IdentExpr(IdentUse(parseIdent()))
            TokenKind.If -> parseIf()
            TokenKind.While -> parseWhile()
            TokenKind.Return -> parseReturn()
            TokenKind.LBrace -> parseBlock()
            else -> unreachable()
        }
    }

    fun parseInfixExpr(lhs: Expr, op: Op) : Expr {
        val rhs = parse_expr_prec(op.prec().next());
        return InfixExpr(lhs, rhs, op)
    }

    fun parsePostfixExpr(lhs: Expr, op : Op) : Expr {
        return when( op ){
            Op.Inc ,
            Op.Dec -> PostfixExpr( lhs, op )
            Op.LeftParen -> FnCallExpr(
                lhs,
                parse_expr_list(TokenKind.Comma, TokenKind.RParen)
            )
            Op.LeftBracket -> unreachable()
            Op.Dot -> FieldExpr(
                lhs,
                IdentUse(parseIdent()),
                null
            )
            else -> unreachable()
        }
    }

    fun parse_expr_prec(prec: Prec) : Expr {
        var op = parseOperator();
        var lhs = when( op ){
            null -> parsePrimaryExpr()
            else -> parsePrefixExpr(op)
        }

        while(true) {
            if( enter(TokenEnter.NL) ){
                break
            }

            op = parseOperator()
            if( op != null){
                lhs = if( op.isInfix() ){
                    if( prec > op.prec() ){
                        last_op = op
                        break
                    }

                    parseInfixExpr(lhs, op)
                } else if( op.isPostfix() ){
                    if( prec == Prec.Top ){
                        last_op = op
                        break
                    }

                    parsePostfixExpr(lhs, op)
                } else {
                    unreachable()
                }
            }else{
                break
            }
        }

        return lhs
    }

    fun parseLiteral() : Literal {
        val token = lex()
        val sym = token.symbol
        val str = sym!!.value

        return when(token.kind){
            TokenKind.LitReal -> RealLiteral(str.toDouble())
            TokenKind.LitInt -> IntLiteral(str.toInt())
            TokenKind.LitStr -> StrLiteral(str)
            TokenKind.LitChar -> CharLiteral(str.toCharArray().first())
            TokenKind.LitBool -> BoolLiteral(str.toBoolean())
            else -> unreachable()
        }
    }

    fun parse_expr() : Expr {
        val result = parse_expr_prec(Prec.Bottom);
        accept(TokenKind.Semicolon);
        return result
    }

    fun parseStmt() : Stmt{
        return when( kind() ){
            TokenKind.Let -> parse_decl()
            else -> ExprStmt(parse_expr())
        }
    }

    fun parseIf() : Expr{
        expect(TokenKind.If);
        val condition = parse_expr();
        val ifBranch = parseBlock();

        val elseBranch = if( accept(TokenKind.Else) ){
            if( isKind(TokenKind.If) ){
                parseIf()
            }else{
                parseBlock()
            }
        }else{
            null
        }

        return IfExpr(condition, ifBranch, elseBranch)
    }

    fun parseWhile() : Expr{
        expect(TokenKind.While);
        val condition = parse_expr();
        isKind(TokenKind.LBrace);
        val body = parseBlock();

        val elseBranch = if( accept(TokenKind.Else) ){
            if( isKind(TokenKind.If) ){
                parseIf()
            }else{
                parseBlock()
            }
        }else{
            null
        }

        return WhileExpr(condition, body, elseBranch)
    }

    fun parse_decl() : Stmt{
        accept(TokenKind.Let);
        val ident = parseIdent();

        var astType : ASTTy? = null
        if( accept(TokenKind.Colon) ){
            astType = parseType()
        }

        val init = if( accept(TokenKind.Assign) ){
            parse_expr()
        }else{
            null
        }

        return LetStmt(
            LocalDecl( ident, astType  ),
            init
        )
    }

    fun parse_expr_list(separator: TokenKind, delimiter: TokenKind) : List<Expr> {
        val exprs = mutableListOf<Expr>()
        while( !accept(delimiter) ){
            if( !exprs.isEmpty() ){
                expect(separator);
            }
            exprs.add(parse_expr());
        }
        return exprs;
    }

    fun parse_statement_list() : List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        while( !accept(TokenKind.RBrace) ){
            if( !stmts.isEmpty() ){
                assert(enter(TokenEnter.NL)) { "Statement does not start with a new line" }
            }
            stmts.add(parseStmt());
        }
        return stmts;
    }

    fun parseParam() : Decl{
        val ident = parseIdent();
        accept(TokenKind.Colon);

        return LocalDecl(
            ident,
            parseType()
        )
    }

    fun parseParamList() : List<Decl> {
        val params = mutableListOf<Decl>()
        while( !accept(TokenKind.RParen) ){
            if(params.isNotEmpty()){
                expect(TokenKind.Comma);
            }
            params.add(parseParam());
        }
        return params;
    }

    fun parse_member_list() : List<Decl> {
        val exprs = mutableListOf<Decl>()
        while( !accept(TokenKind.RBrace) ){
            if(exprs.isNotEmpty()){
                expectEnter(TokenEnter.NL);
            }
            exprs.add(parse_member(exprs.size));
        }
        return exprs;
    }

    fun parse_type_list() : List<ASTTy> {
        val types = mutableListOf<ASTTy>()
        while( !accept(TokenKind.RParen) ){
            if(types.isNotEmpty()){
                expect(TokenKind.Comma);
            }
            types.add(parseType());
        }
        return types;
    }
}
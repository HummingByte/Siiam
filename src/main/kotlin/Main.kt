fun main(args: Array<String>) {

    val source = Source.new("test.si")
    val symTable = SymTable()
    val lexer = Lexer(source, symTable)

/*
    while(true){
        val token = lexer.nextToken();
        if(token.kind == TokenKind.Error || token.kind == TokenKind.Eof){
            break
        }

        println(token.kind)
    }*/

    var parser = Parser(lexer, symTable)

    val mod = parser.parseModule()

    val binder = Binder()
    binder.bind(mod)

    val tyTable = TyTable()
    val checker = TypeChecker(tyTable)
    checker.check(mod)

    var world = World()




    println("test")


}
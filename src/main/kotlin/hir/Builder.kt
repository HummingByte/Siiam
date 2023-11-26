package hir

class Builder(val world : World) {
    fun constructor() : Constructor {
        return Constructor(world)
    }

    fun dataDef(value : Data) : Def {
        val ax = world.axiom(Axiom.Data)
        return DataDef(ax, value)
        //return world.insert(def)
    }

    fun nodeDef(ax : Def, ops : Array<Def>) : Def {
        return NodeDef(ax, ops)
        //return world.insert(def)
    }

    fun nodeDef(ax : Def, ops : Array<Def>, dbg : String) : Def {
        val result = NodeDef(ax, ops)
        result.dbg = dbg
        return result
    }

    fun nothing() : Def {
        return world.axiom(Axiom.Nothing)
    }

    fun bot() : Def {
        return world.axiom(Axiom.Bot)
    }

    fun tyInt(width: Int) : Def {
        val tyAxiomDef = world.axiom(Axiom.TyInt)
        val data = IntData(width)
        val valueDef = dataDef(data)
        return nodeDef(tyAxiomDef, arrayOf(valueDef), "tyInt")
    }

    fun tyIdx(width: Int) : Def {
        val tyAxiomDef = world.axiom(Axiom.TyIdx)
        val data = IntData(width)
        val valueDef = dataDef(data)
        return  nodeDef(tyAxiomDef, arrayOf(valueDef), "tyInt")
    }

    fun tyReal(width: Int) : Def {
        val tyAxiomDef = world.axiom(Axiom.TyReal)
        val data = IntData(width)
        val valueDef = dataDef(data)
        return nodeDef(tyAxiomDef, arrayOf(valueDef), "tyReal")
    }

    fun lit(value: Int, ty: Def) : Def {
        val literalAx = world.axiom(Axiom.Lit)
        val data = IntData(value)
        val valueDef = dataDef(data)
        return nodeDef(literalAx, arrayOf(ty, valueDef), "lit")
    }

    fun litInt( width: Int, idx: Int ) : Def {
        val arity = tyInt(width)
        return lit(idx, arity)
    }

    fun litIdx( width: Int, idx: Int ) : Def {
        val arity = tyIdx(width)
        return lit(idx, arity)
    }

    fun lam(ty : Def) : Def {
        val ax = world.axiom(Axiom.Lam)
        return nodeDef(ax, arrayOf(ty, nothing()), "lam")
    }

    fun `var`(lam: Def) : Def {
        val ax = world.axiom(Axiom.Var)
        return nodeDef(ax, arrayOf(lam), "var")
    }

    fun ret(lam: Def) : Def {
        val ax = world.axiom(Axiom.Ret)
        return nodeDef(ax, arrayOf(lam), "ret")
    }

    fun unit() : Def {
        return tuple(arrayOf())
    }

    fun tyUnit() : Def {
        return sigma(arrayOf())
    }

    fun tuple(elems: Array<Def>) : Def {
        val ax = world.axiom(Axiom.Tuple)
        return nodeDef(ax,elems, "tuple")
    }

    fun sigma(elems: Array<Def>) : Def {
        val ax = world.axiom(Axiom.Tuple)
        return nodeDef(ax,elems, "sigma")
    }

    fun pack(shape: Def, body: Def) : Def {
        val ax = world.axiom(Axiom.Pack)
        return nodeDef(ax, arrayOf(shape, body), "pack")
    }

    fun extract(tup: Def, index: Def) : Def {
        val ax = world.axiom(Axiom.Extract)
        return nodeDef(ax, arrayOf(tup, index), "extract")
    }

    fun app(callee: Def, args: Array<Def>) : Def {
        val arg = tuple(args)
        return app(callee, arg)
    }

    fun app(callee: Def, arg: Def) : Def {
        val ax = world.axiom(Axiom.App)
        return nodeDef(ax, arrayOf(callee, arg), "app")
    }

    fun pi(domain: Def, co_domain : Def) : Def {
        val ax = world.axiom(Axiom.Pi)
        return nodeDef(ax, arrayOf(domain, co_domain), "pi")
    }

    fun add(lhs: Def, rhs: Def) : Def {
        val ax = world.axiom(Axiom.Add)
        val arg = tuple(arrayOf(lhs, rhs))
        return app(ax, arg)
    }

    fun sub(lhs: Def, rhs: Def) : Def {
        return if( lhs == rhs ){
            litInt(32, 0)
        }else {
            val ax = world.axiom(Axiom.Sub)
            val arg = tuple(arrayOf(lhs, rhs))
            app(ax, arg)
        }
    }

    fun mul(lhs: Def, rhs: Def) : Def {
        val ax = world.axiom(Axiom.Mul)
        val arg = tuple(arrayOf(lhs, rhs))
        return app(ax, arg)
    }

    fun div(lhs: Def, rhs: Def) : Def {
        val ax = world.axiom(Axiom.Div)
        val arg = tuple(arrayOf(lhs, rhs))
        return app(ax, arg)
    }

    fun gt(lhs: Def, rhs: Def) : Def {
        val ax = world.axiom(Axiom.Gt)
        val arg = tuple(arrayOf(lhs, rhs))
        return app(ax, arg)
    }

    fun ne(lhs: Def, rhs: Def) : Def {
        val ax = world.axiom(Axiom.Ne)
        val arg = tuple(arrayOf(lhs, rhs))
        return app(ax, arg)
    }

    fun slot(ty: Def, mem: Def) : Def {
        val ax = world.axiom(Axiom.Slot)
        val curry = nodeDef(ax, arrayOf(ty))
        return app(curry, arrayOf(mem))
    }

    fun alloc(ty: Def, mem: Def) : Def {
        val ax = world.axiom(Axiom.Alloc)
        val curry = nodeDef(ax, arrayOf(ty))
        return app(curry, arrayOf(mem))
    }

    fun load(mem: Def, ptr: Def) : Def {
        val ax = world.axiom(Axiom.Load)
        val ty = bot()
        val curry = nodeDef(ax, arrayOf(ty))
        return app(curry, arrayOf(mem, ptr))
    }

    fun store(mem: Def, ptr: Def, value: Def) : Def {
        val ax = world.axiom(Axiom.Store)
        val ty = bot()
        val curry = nodeDef(ax, arrayOf(ty))
        return app(curry, arrayOf(mem, ptr, value))
    }

    fun free(mem: Def, ptr: Def) : Def {
        val ax = world.axiom(Axiom.Load)
        val ty = bot()
        val curry = nodeDef(ax, arrayOf(ty))
        return app(curry, arrayOf(mem, ptr))
    }

    fun setBody(lam: Def, body: Def){
        assert(lam.ax == world.axiom(Axiom.Lam))
        lam.setOp(1, body)
    }
}

class Constructor(val world: World){
    private val cyclicSigner = CyclicSigner(world)

    fun construct(def : Def) : Def {
        val new = cyclicSigner.getNew(def)
        return new
    }
}


class Builder(val world : World) {
    fun construct() : Construction{
        return Construction(world)
    }

    fun dataDef(value : Data) : Def {
        val ax = world.axiom(Axiom.Data)
        val def = DataDef(ax, value)
        world.insert(def)
    }
    
    fun lit(value: u32, ty: Def) : Def {
        val literalAx = world.axiom(Axiom.Lit)
        val data = Data::from::<u32>(value)
        val value_def = data_def(data)
        val literal = node_def(literalAx, array![ty.link, value_def.link])
        literal
    }
}

class Construction(val world: World){
    val cyclicSigner = CyclicSigner(world)
}
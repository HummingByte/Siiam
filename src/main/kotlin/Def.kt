import java.nio.ByteBuffer
import kotlin.math.min

enum class DefState{
    Constructed, Pending
}

enum class Axiom{
    Bot, Data,
    Tuple, Sigma, Pack, Extract,
    App, Pi, Lam, Var, Ret,
    Lit,
    TyIdx, TyInt, TyReal, TyStr,
    Nothing,
    Add, Sub, Mul, Div,
    Gt, Ne,
    Slot, Alloc, Store, Load, Free, Ptr, Mem
}


class DefKey(val def: Def){
    override fun hashCode(): Int {
        return def.hash ?: 0
    }

    override fun equals(other: Any?): Boolean {
        if(other !is Def) return false
        return NodeComparator().similar(def, other)
    }
}

abstract class Def(val world: World){
    var ax: Def? = null
    var hash: Int? = null
    var axiom: Axiom? = null
    var dbg : String? = null

    constructor(ax: Def) : this(ax.world){
        this.ax = ax
    }

    open fun setOp(idx: Int, op : Def){
        unreachable<Unit>()
    }
}

class EmptyDef(world: World) : Def(world)
class DataDef(ax: Def, val data : Data) : Def(ax)

class NodeDef(ax: Def, val ops : Array<Def>) : Def(ax){
    override fun setOp(idx: Int, op: Def) {
        ops[idx] = op
    }
}

class NodeComparator(){
    private val visited = HashSet<Def>()
    fun similar( left : Def, right : Def ) : Boolean{
        when(left){
            right -> return true
            is DataDef -> {
                if(right !is DataDef) return false
                return left.data == right.data
            }
            is NodeDef -> {
                if(right !is NodeDef) return false
                if(!visited.add(left)) return true
                if(left.hash != right.hash) return false
                if(left.ax == null){
                    if(right.ax != null) return false
                }else if(!similar(left.ax!!, right.ax!!)) return false

                for( (leftOp, rightOp) in left.ops.zip(right.ops) ){
                    if(!similar(leftOp, rightOp)) return false
                }
                return true
            }
            else -> return false
        }
    }
}

class World{
    val sea = HashMap<DefKey, Def>()
    val axiom2def = HashMap<Axiom, Def>()
    val empty = EmptyDef(this)

    init {
        val root = EmptyDef(this)
        root.hash = 0xffffffff.toInt()
        var prev : Def = root

        var signer = AsyncSigner()

        for( axiom in Axiom.values() ){
            val axiomDef = NodeDef(
                root,
                arrayOf(prev)
            )

            axiomDef.axiom = axiom

            val hash = signer.sign(axiomDef)
            axiomDef.hash = hash
            insert(axiomDef)
            axiom2def[axiom] = axiomDef
            prev = axiomDef
        }

        println("test")
    }

    fun axiom(axiom: Axiom) : Def{
        return axiom2def[axiom]!!
    }

    fun insert(def : Def) : Def{
        assert(def.hash != null)
        return sea.putIfAbsent(DefKey(def), def) ?: def
    }
}

class SignNode(var index : Int, var def: Def){
    var lowLink : Int = index
    var closed: Boolean = false
    var unique: SignNode? = null
    var hashes = Array(2){0}

    fun unique() :  SignNode{
        return unique?.unique() ?: this
    }
}

class CyclicSigner(val world: World){
    private val nodes = HashMap<Def, SignNode>()
    private val old2new = HashMap<Def, Def>()
    private val old2node = HashMap<Def, SignNode>()

    fun createNode(def: Def) : SignNode{
        return nodes.computeIfAbsent(def){ SignNode(
            nodes.size,
            def
        )}
    }

    fun getNew(old : Def) : Def{
        if(old.hash != null) return old
        val new = old2new[old]
        if(new != null) return new
        discover(old)
        return old2new[old]!!
    }

    private fun discover(def: Def) : Boolean{
        if(def.hash != null) return false
        if(nodes.containsKey(def)) return true

        val currentNode = createNode(def)

        if(def is NodeDef){
            for( op in def.ops ){
                if(discover(op)){
                    val depNode = createNode(op)
                    if( !depNode.closed ){
                        currentNode.lowLink = min(currentNode.lowLink, depNode.lowLink)
                    }
                }
            }
        }

        if( currentNode.index == currentNode.lowLink ){
            sign(def)
        }

        return true
    }

    fun sign(def : Def){
        val oldDefs = ArrayList<Def>()
        collect(def, oldDefs)

        createNewDefs(oldDefs)


        blend(oldDefs)
        val old2new = disambiguate(oldDefs)
        addMapping(oldDefs, old2new)
    }

    fun collect(def : Def, list : MutableList<Def>){
        val node = nodes[def]
        if(node != null){
            if( node.index == node.lowLink && list.isNotEmpty() || node.closed ){
                return
            }

            list.add(def)
            node.closed = true
            if( def is NodeDef ){
                for( op in def.ops ){
                    collect(op, list)
                }
            }
        }
    }

    fun blend(defs: Collection<Def>){
        for( epoch in defs.indices){
            for( def in defs ){
                signNode(def, epoch % 2)
            }
        }
    }
    
    fun signNode(def : Def, slot: Int){
        val node = createNode(def)
        val hasher = Hasher()

        hasher.update(def.ax!!.hash!!)
        when( def ){
            is NodeDef -> {
                for( op in def.ops ){
                    val opHash = op.hash
                    val sign = if(opHash != null){
                        opHash
                    }else{
                        val depNode = createNode(op)
                        depNode.unique().hashes[slot]

                        /*
                        val new = old2new[depNode.def]
                        if(new != null) {
                            new.hash ?: unreachable()
                        }else{
                            depNode.unique().hashes[slot]
                        }*/
                    }
    
                    hasher.update(sign)
                }
            }
            is DataDef -> {
                def.data.hash(hasher)
            }
        }

        node.hashes[1 - slot] = hasher.finalize()
    }

    fun disambiguate(oldDefs: List<Def>){
        val old2new = if( oldDefs.size == 1 ){
            createNewDefs(oldDefs, oldDefs)
        }else{
            val uniqueDefs = filterUniqueDefs(oldDefs)
            val newUniqueDefs = createNewDefs(oldDefs, uniqueDefs)

            if( uniqueDefs.size != oldDefs.size ){
                blend(newUniqueDefs.values)
            }

            val offset = uniqueDefs.size % 2
            for( def in newUniqueDefs ){
                val node = createNode(def)
                val sign = node.hashes[uniqueDefs.size % 2]
                newNode.hash = sign
            }
        }
    }

    fun createNewDefs(uniqueDefs: Collection<Def>) : Map<Def, Def>{
        val old2new = HashMap<Def, Def>()

        for( old in uniqueDefs ){
            val node = createNode(old)

            val newNode = if(old is NodeDef){
                val newNode = NodeDef(old.ax!!, Array(old.ops.size){world.empty})
                old2new[old] = newNode
                newNode
            }else if(old is DataDef){
                old
            }else{
                continue
            }
        }

        for( old in uniqueDefs ){
            if( old is NodeDef ){
                val new = old2new[old]
                if( new is NodeDef ){
                    for( idx in 0 until old.ops.size){
                        val op = old.ops[idx]
                        val newOp = old2new[op]
                        val newLink = newOp ?: op
                        new.ops[idx] = newLink
                    }
                }
            }
        }

        return old2new
    }

    fun addMapping(oldDefs: List<Def>, old2new : HashMap<Def, Def>){
        for( old in oldDefs ){
            val node = createNode(old)
            val uniqueNode = node.unique

            val new = if( uniqueNode == null ){
                val uniqueDef = old2new[old]

                if(uniqueDef == null){
                    old
                }else{
                    world.insert(uniqueDef)
                    uniqueDef
                }
            }else{
                this.old2new[uniqueNode.def]!!
            }

            if(old::class != new::class){
                unreachable<Unit>()
            }

            this.old2new[old] = new
        }
    }

    fun filterUniqueDefs(old_defs: List<Def>) : Collection<Def>{
        var uniqueMap = HashMap<Int, Def>()
        val len = old_defs.size

        for( old in old_defs ){
            val node = createNode(old)
            val sign = node.hashes[len % 2]

            val uniqueNode = uniqueMap[sign]
            if( uniqueNode != null ){
                node.unique = createNode(uniqueNode)
            }else{
                uniqueMap[sign] = old
            }
        }

        val values = uniqueMap.values

        for( value in values ){
            if( value is NodeDef ){
                val ops = value.ops
                for( (idx, op) in ops.withIndex() ){
                    val node = createNode(op)
                    val unique = node.unique
                    if(unique != null){
                        ops[idx] = unique.def
                    }
                }
            }
        }

        return values
    }
}

class AsyncSigner(){
    fun sign(def: Def) : Int{
        val hasher = Hasher()

        hasher.update(def.ax!!.hash!!)
        when(def){
            is NodeDef -> {
                for(op in def.ops){
                    hasher.update(op.hash!!)
                }
            }

            is DataDef -> {
                def.data.hash(hasher)
            }
        }

        return hasher.finalize()
    }
}


class Hasher{
    var h : UInt = 0u
    var len : UInt = 0u

    private companion object{
        fun murmur3Scramble(x: UInt ) : UInt {
            var k = x * 0xcc9e2d51u
            k = (k shl 15) or (k shr 17)
            k *= 0x1b873593u
            return k
        }
        
        fun murmur3( x: UInt, key: UInt) : UInt{
            var h = x xor murmur3Scramble(key)
            h = (h shl 13) or (h shr 19)
            h = h * 5u + 0xe6546b64u
            return h
        }

        fun murmur3Finalize(x : UInt, len: UInt) : UInt{
            var h = x xor len
            h = h xor ( h shr 16 )
            h *= 0x85ebca6bu
            h = h xor ( h shr 13 )
            h *= 0xc2b2ae35u
            h = h xor ( h shr 16 )
            return h
        }
    }

    private fun updateImpl(x: UInt){
        h = murmur3(h, x)
        len+=4u
    }

    fun update(x: UInt){
        updateImpl(x)
    }

    fun update(x: Int){
        update(x.toUInt())
    }

    fun update(x: Long){
        update(x.toInt())
        update(x.shr(32).toInt())
    }

    fun update(f : Float){
        update(f.toBits())
    }

    fun update(d : Double){
        update(d.toBits())
    }

    fun update(arr: ByteArray){
        var intBuffer = ByteBuffer.wrap(arr).asIntBuffer()
        intBuffer.reset()

        while(intBuffer.hasRemaining()){
            update(intBuffer.get())
        }
    }

    fun finalize() : Int {
        return murmur3Finalize(h, len).toInt()
    }
}


package hir

import frontend.unreachable
import java.nio.ByteBuffer
import kotlin.math.min

enum class DefState{
    Constructed, Pending
}

enum class Axiom{
    Data, Bot,
    Tuple, Sigma, Pack, Extract,
    App, Pi, Lam, Var, Ret,
    Lit,
    TyIdx, TyInt, TyReal, TyStr,
    Nothing,
    Add, Sub, Mul, Div,
    Gt, Ne,
    Slot, Alloc, Store, Load, Free, Ptr, Mem
}


class DefKey(val def: Def, val hash : Int){
    constructor(def : Def) : this(def, def.hash ?: 0)

    override fun hashCode(): Int {
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if(other !is DefKey) return false
        return NodeComparator().similar(def, other.def)
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


class NodeComparator{
    private val visited = HashMap<Def, Def>()
    fun similar(left : Def, right : Def) : Boolean{
        when(left){
            right -> return true
            is DataDef -> {
                if(right !is DataDef) return false
                return left.data == right.data
            }
            is NodeDef -> {
                if(right !is NodeDef) return false
                visited.putIfAbsent(left, right)?.let {
                    return it == right
                }

                if(left.hash != right.hash) return false
                if(left.ax == null){
                    if(right.ax != null) return false
                }else if(!similar(left.ax!!, right.ax!!)){
                    return false
                }

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

    fun axiom(axiom: Axiom) : Def {
        return axiom2def[axiom]!!
    }

    fun insert(def : Def) : Def {
        assert(def.hash != null)
        return sea.putIfAbsent(DefKey(def), def) ?: def
    }
}

class SignNode(var index : Int, var def: Def){
    var lowLink : Int = index
    var closed: Boolean = false
    var parent: SignNode? = null
    var hashes = Array(2){0}

    fun unique() : SignNode {
        return parent?.unique() ?: this
    }
}

class CyclicSigner(val world: World){
    private val nodes = HashMap<Def, SignNode>()
    private val old2new = HashMap<Def, Def>()

    private fun getNode(def: Def) : SignNode {
        return nodes.computeIfAbsent(def){ SignNode(
            nodes.size,
            def
        )
        }
    }

    fun getNew(old : Def) : Def {
        if(old.hash != null) return old
        val new = old2new[old]
        if(new != null) return new
        discover(old)
        return old2new[old]!!
    }

    private fun discover(def: Def) : Boolean{
        if(def.hash != null) return false
        if(nodes.containsKey(def)) return true

        val currentNode = getNode(def)

        if(def is NodeDef){
            for( op in def.ops ){
                if(discover(op)){
                    val depNode = getNode(op)
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
        val groupDefs = ArrayList<Def>()
        collect(def, groupDefs)

        val old2new = createNewDefs(groupDefs)
        val values = old2new.values
        blend(values)
        disambiguate(values)
        for( (old, new) in old2new.entries ){
            val uniqueNode = getNode(new).unique()
            val uniqueDef = uniqueNode.def
            this.old2new[old] = uniqueDef
        }

        val test = this.old2new[def]

    }

    fun collect(def : Def, list : MutableList<Def>){
        val node = nodes[def]
        if(node != null){
            if( node.index == node.lowLink && list.isNotEmpty() || node.closed ){
                return
            }

            list.add(def)
            node.closed = true
            if( def is NodeDef){
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
        val node = getNode(def)
        val hasher = Hasher()

        hasher.update(def.ax!!.hash!!)
        when( def ){
            is NodeDef -> {
                for( op in def.ops ){
                    val opHash = op.hash
                    val sign = if(opHash != null){
                        opHash
                    }else{
                        val depNode = getNode(op)
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

    fun disambiguate(oldDefs: Collection<Def>) : Collection<Def>{
        val uniqueDefs = if( oldDefs.size == 1 ){
            oldDefs
        }else{
            val uniqueDefs = filterUniqueDefs(oldDefs)

            if( uniqueDefs.size != oldDefs.size ){
                for( def in uniqueDefs ){
                    getNode(def).hashes = arrayOf(0, 0)
                }
                blend(uniqueDefs)

            }

            uniqueDefs
        }

        val offset = uniqueDefs.size % 2
        for( def in uniqueDefs ){
            def.hash = getNode(def).hashes[offset]
        }
        return uniqueDefs
    }

    fun createNewDefs(defs: Collection<Def>) : Map<Def, Def>{
        val old2new = HashMap<Def, Def>()

        for( old in defs ){
            if(old is NodeDef){
                val newNode = NodeDef(old.ax!!, Array(old.ops.size){world.empty})
                newNode.dbg = old.dbg
                old2new[old] = newNode
            }else if(old is DataDef){
                old2new[old] = old
            }
        }

        for( old in defs ){
            if( old is NodeDef){
                val new = old2new[old] as NodeDef
                for( (idx, op) in old.ops.withIndex()){
                    new.ops[idx] = old2new[op] ?: this.old2new[op] ?: op
                }
            }
        }

        return old2new
    }

    fun addMapping(oldDefs: List<Def>, old2new : HashMap<Def, Def>){
        for( old in oldDefs ){
            val node = getNode(old)
            val uniqueNode = node.parent

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

    fun filterUniqueDefs(old_defs: Collection<Def>) : Collection<Def>{
        val uniqueMap = HashMap<DefKey, Def>()
        val offset = old_defs.size % 2

        for( old in old_defs ){
            val node = getNode(old)
            val hash = node.hashes[offset]

            val key = DefKey(old, hash)
            val uniqueNode = uniqueMap[key]
            if( uniqueNode != null ){
                node.parent = getNode(uniqueNode)
                old.hash = hash
            }else{
                uniqueMap[key] = old
            }
        }

        val values = uniqueMap.values

        for( value in values ){
            if( value is NodeDef){
                val ops = value.ops
                for( (idx, op) in ops.withIndex() ){
                    val node = getNode(op)
                    ops[idx] = node.unique().def
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


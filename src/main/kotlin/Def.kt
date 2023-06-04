import java.nio.ByteBuffer
import kotlin.math.min

enum class DefState{
    Constructed, Pending
}

open class Def(val ax: Def?){
    var hash: Int? = null
    //var state : DefState = DefState.Pending
    
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


object EmptyDef : Def(null)

class DataDef(ax: Def, val data : ByteArray) : Def(ax)
class NodeDef(ax: Def, val ops : Array<Def>) : Def(ax)

class World{
    val sea = HashMap<Int, Def>()
    val axiom2def = HashMap<Axiom, Def>()

    init {
        val root = EmptyDef
        root.hash = 0xffffffff.toInt()
        var prev : Def = root


        var signer = AsyncSigner()

        for( axiom in Axiom.values() ){
            val axiomDef = NodeDef(
                root,
                arrayOf(prev)
            )

            var hash = signer.sign(axiomDef)
            axiomDef.hash = hash
            insertDef(hash, axiomDef)
            axiom2def[axiom] = axiomDef
            prev = axiomDef
        }

        println("test")
    }

    fun axiom(axiom: Axiom) : Def{
        return axiom2def[axiom]!!
    }

    fun insert(def : Def){
        val hash = def.hash ?: return unreachable()

        insertDef(hash, def)
    }

    fun insertDef(hash: Int, def: Def){
        if(sea.containsKey(hash)){
            println("Double hash appeard")
            unreachable<Unit>()
        }

        sea[hash] = def
    }
}

class SignNode(var index : Int){
    var lowLink : Int = index
    var closed: Boolean = false
    var link: Def? = null
    var unique: SignNode? = null
    var signs = Array<Int>(2){0}

    fun unique() :  SignNode{
        return unique?.unique() ?: this
    }
}

class CyclicSigner(val world: World){
    val nodes = HashMap<Def, SignNode>()
    val old2new = HashMap<Def, Def>()

    fun getNode(def: Def) : SignNode{
        return nodes.computeIfAbsent(def){ SignNode(
            nodes.size
        )}
    }

    fun discover(def: Def) : Boolean{
        if(nodes.containsKey(def)) return true
        if(def.hash != null) return false

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
        val oldDefs = ArrayList<Def>()
        collect(def, oldDefs)
        blend(oldDefs)
        disambiguate(oldDefs)
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
                    collect(def, list)
                }
            }
        }
    }

    fun blend(defs: Collection<Def>){
        for( epoch in 0 .. defs.size ){
            for( def in defs ){
                signNode(def, epoch % 2)
            }
        }
    }
    
    fun signNode(def : Def, slot: Int){
        val node = getNode(def)
        val hash = Hasher()

        hash.update(def.ax!!.hash!!)
        when( def ){
            is NodeDef -> {
                for( op in def.ops ){
                    val opHash = op.hash
                    val sign = if(opHash != null){
                        opHash
                    }else{
                        val dep_node = getNode(op)
        
                        val new = old2new[dep_node.link]
                        if(new != null) {
                            new.hash ?: unreachable()
                        }else{
                            dep_node.unique().signs[slot]
                        }
                    }
    
                    hash.update(sign)
                }
            }
            is DataDef -> {
                hash.update(def.data)
            }
        }

        node.signs[1 - slot] = hash.finalize()
    }

    fun disambiguate(old_defs: List<Def>){
        if( old_defs.size == 1){
            createNewDefs(old_defs, old_defs)
        }else{
            val uniqueDefs = filterUniqueDefs(old_defs)

            if( uniqueDefs.size != old_defs.size ){
                for( old in uniqueDefs ){
                    val node = getNode(old)
                    node.signs = arrayOf(0, 0)
                }

                blend(uniqueDefs)
            }

            createNewDefs(old_defs, uniqueDefs)
        }
    }

    fun createNewDefs(old_defs: List<Def>, unique_defs: Collection<Def>){
        val map = HashMap<Def, Def>()

        for( def in unique_defs ){
            val opLen = if(def is NodeDef){
                def.ops.size
            }else{
                0
            }

            val node = getNode(def)
            val sign = node.signs[unique_defs.size % 2]
            val newNode = NodeDef(def.ax!!, Array<Def>(opLen){EmptyDef})
            newNode.hash = sign
            map[def] = newNode
        }

        for( old in unique_defs ){
            if( old is NodeDef ){
                val new = map[old]
                for( idx in 0 .. old.ops.size){
                    val op = old.ops[idx]
                    val newOp = map[op]
                    val newLink = newOp ?: op
    
                    if( new is NodeDef ){
                        new.ops[idx] = newLink
                    }
                }
            }
        }
        
        add_mapping(old_defs, map)
    }

    fun add_mapping(defs: List<Def>, map : HashMap<Def, Def>){
        for( old in defs ){
            val node = getNode(old)
            val uniqueNode = node.unique

            val new = if( uniqueNode == null ){
                val uniqueDef = map.remove(old)!!
                world.insert(uniqueDef)
                uniqueDef
            }else{
                old2new[uniqueNode.link]!!
            }

            old2new[old] = new
        }
    }

    fun filterUniqueDefs(old_defs: List<Def>) : Collection<Def>{
        var uniqueMap = HashMap<Int, Def>()
        val len = old_defs.size

        for( old in old_defs ){
            val node = getNode(old)
            val sign = node.signs[len % 2]

            val uniqueNode = uniqueMap[sign]
            if( uniqueNode != null ){
                node.unique = getNode(uniqueNode)
            }else{
                uniqueMap[sign] = old
            }
        }

        return uniqueMap.values
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
                hasher.update(def.data)
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


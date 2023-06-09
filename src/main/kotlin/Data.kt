import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.charset.Charset

abstract class Data{
    abstract fun hash(hasher: Hasher)
}

class IntData(val value : Int) : Data(){
    override fun hash(hasher: Hasher) {
        hasher.update(value)
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other !is IntData) return false
        return value == other.value
    }
}
class LongData(val value : Long) : Data(){
    override fun hash(hasher: Hasher) {
        hasher.update(value)
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other !is LongData) return false
        return value == other.value
    }
}
class FloatData(val value : Float) : Data(){
    override fun hash(hasher: Hasher) {
        hasher.update(value)
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other !is FloatData) return false
        return value == other.value
    }
}
class DoubleData(val value : Double) : Data(){
    override fun hash(hasher: Hasher) {
        hasher.update(value)
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other !is DoubleData) return false
        return value == other.value
    }
}
class StringData(val value : String) : Data(){
    override fun hash(hasher: Hasher) {
        hasher.update(value.toByteArray(Charset.defaultCharset()))
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other !is StringData) return false
        return value == other.value
    }
}
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
}
class LongData(val value : Long) : Data(){
    override fun hash(hasher: Hasher) {
        hasher.update(value)
    }
}
class FloatData(val value : Float) : Data(){
    override fun hash(hasher: Hasher) {
        hasher.update(value)
    }
}
class DoubleData(val value : Double) : Data(){
    override fun hash(hasher: Hasher) {
        hasher.update(value)
    }
}
class StringData(val value : String) : Data(){
    override fun hash(hasher: Hasher) {
        hasher.update(value.toByteArray(Charset.defaultCharset()))
    }
}
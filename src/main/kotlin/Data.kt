import java.nio.ByteBuffer
import java.nio.IntBuffer

abstract class Data{
    abstract fun collect(buffer: ByteBuffer)
}

class IntData(val value : Int) : Data(){
    override fun collect() {
        IntBuffer.
    }

}
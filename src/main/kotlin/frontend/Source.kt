package frontend

import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.StringBuilder

class Source(var reader : BufferedReader) {
    var eof : Boolean = false

    var last: Pos = Pos(1, 0)
    var current: Pos = Pos(1, 0)
    var lastChar: Char = 0.toChar()
    var currentChar: Char = 0.toChar()

    init {
        next()
    }

    fun peek() : Char? {
        return if(eof){
            null
        }else{
            this.currentChar
        }
    }

    fun next() : Char? {
        val prev = this.peek()

        if (prev != null) {
            this.updatePosition(prev)
            val next = this.reader.read()
            this.eof = next == -1
            currentChar = next.toChar()
        }

        return prev
    }

    fun acceptString(str: StringBuilder, pred : (Char) -> Boolean) : Boolean{
        return this.accept{
            if( pred(it) ){
                str.append(it)
                true
            }else{
                false
            }
        }
    }

    fun acceptString(str: StringBuilder) : Boolean{
        val ch = this.peek()
        if( ch != null){
            str.append(ch)
            this.next()
            return true
        }

        return false
    }

    fun accept( pred : (Char) -> Boolean ) : Boolean{
        val ch = this.peek()
        if( ch != null ){
            if(pred(ch)){
                this.next()
                return true
            }
        }

        return false
    }

    fun updatePosition(c: Char) {
        if( c == '\r' || c == '\n' ){
            if( this.lastChar == '\n' &&  c == '\r'
                || this.lastChar == '\r' &&  c == '\n' ){
                this.lastChar = 0.toChar()
            } else {
                this.current.col = 1
                this.current.line += 1
                this.lastChar = c
            }
        } else {
            this.current.col += 1;
            this.lastChar = c;
        }
    }

    fun resetLoc(){
        this.last = this.current
    }

    fun loc() : Span {
        return Span(last, current)
    }

    companion object{
        fun new( file : String ) : Source {
            return Source(BufferedReader(InputStreamReader(FileInputStream(file))))
        }
    }
}

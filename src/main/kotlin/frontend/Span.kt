package frontend

public class Span(val begin: Pos, val end: Pos) {

    companion object{
        private val ZERO = Span(Pos.zero(), Pos.zero())

        fun zero() : Span {
            return ZERO
        }
    }
}
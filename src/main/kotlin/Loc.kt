public class Loc(val begin: Pos, val end: Pos) {

    companion object{
        private val ZERO = Loc(Pos.zero(), Pos.zero())

        fun zero() : Loc{
            return ZERO
        }
    }
}
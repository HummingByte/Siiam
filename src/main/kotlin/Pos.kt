class Pos(var line: Int, var col: Int){
    companion object{
        private val ZERO = Pos(0, 0)

        fun zero() : Pos{
            return ZERO
        }
    }
}
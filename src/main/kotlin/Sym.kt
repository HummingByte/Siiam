public class Sym(val value: String){
}

public class SymTable {
    private val id2str: MutableList<String> = arrayListOf()
    private val str2sym: MutableMap<String, Sym> = hashMapOf()

    fun from(name : String) : Sym{
        return str2sym.computeIfAbsent(name){
            Sym(it)
        }
    }
}
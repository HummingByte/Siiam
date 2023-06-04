

interface Ty;

class PrimTy(val kind : PrimTyKind) : Ty

class StructTy(val name: Sym, val members : List<Ty>) : Ty

class FnTy(val params : List<Ty>,
           val ret_ty : Ty?) : Ty

object ErrTy : Ty

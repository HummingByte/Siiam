package backend.llvm

import backend.llvm.type.LLVMIntType


class CmpInst(val kind: CmpInstKind, val lhs: LLVMValue, val rhs: LLVMValue) : LLVMInst(LLVMIntType.get(1)){

    override fun dump(cp: CodePrinter) {
        cp += "%$name = ${kind.op} ${kind.subOp} "
        lhs.remit(cp)
        cp += ", "
        rhs.remit(cp, withType = false)
    }
}
enum class CmpInstKind(val op: String, val subOp: String, val idx: Int){
    FCMP_FALSE("fcmp", "false", 0), ///< 0 0 0 0    Always false (always folded)
    FCMP_OEQ("fcmp", "oeq", 1),   ///< 0 0 0 1    True if ordered and equal
    FCMP_OGT("fcmp", "ogt", 2),   ///< 0 0 1 0    True if ordered and greater than
    FCMP_OGE("fcmp", "oge", 3),   ///< 0 0 1 1    True if ordered and greater than or equal
    FCMP_OLT("fcmp", "olt", 4),   ///< 0 1 0 0    True if ordered and less than
    FCMP_OLE("fcmp", "ole", 5),   ///< 0 1 0 1    True if ordered and less than or equal
    FCMP_ONE("fcmp", "one", 6),   ///< 0 1 1 0    True if ordered and operands are unequal
    FCMP_ORD("fcmp", "ord", 7),   ///< 0 1 1 1    True if ordered (no nans)
    FCMP_UNO("fcmp", "uno", 8),   ///< 1 0 0 0    True if unordered: isnan(X) | isnan(Y)
    FCMP_UEQ("fcmp", "ueq", 9),   ///< 1 0 0 1    True if unordered or equal
    FCMP_UGT("fcmp", "ugt", 10),  ///< 1 0 1 0    True if unordered or greater than
    FCMP_UGE("fcmp", "uge", 11),  ///< 1 0 1 1    True if unordered, greater than, or equal
    FCMP_ULT("fcmp", "ult", 12),  ///< 1 1 0 0    True if unordered or less than
    FCMP_ULE("fcmp", "ule", 13),  ///< 1 1 0 1    True if unordered, less than, or equal
    FCMP_UNE("fcmp", "une", 14),  ///< 1 1 1 0    True if unordered or not equal
    FCMP_TRUE("fcmp", "true", 15), ///< 1 1 1 1    Always true (always folded)
    ICMP_EQ("icmp", "eq", 32),   ///< equal
    ICMP_NE("icmp", "ne", 33),   ///< not equal
    ICMP_UGT("icmp", "ugt", 34),  ///< unsigned greater than
    ICMP_UGE("icmp", "uge", 35),  ///< unsigned greater or equal
    ICMP_ULT("icmp", "ult", 36),  ///< unsigned less than
    ICMP_ULE("icmp", "ule", 37),  ///< unsigned less or equal
    ICMP_SGT("icmp", "sgt", 38),  ///< signed greater than
    ICMP_SGE("icmp", "sge", 39),  ///< signed greater or equal
    ICMP_SLT("icmp", "slt", 40),  ///< signed less than
    ICMP_SLE("icmp", "sle", 41);  ///< signed less or equal
}
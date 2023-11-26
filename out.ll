%Node = type {i32, %Node}

define i32 @main(i32 %a){
alloc:
  %.0 = alloca i32
  store i32 %a, i32* %.0, align 4
  %.3 = alloca i32
  br label %entry
entry:
  store i32 9, i32* %.3, align 4
  %.5 = load i32, i32* %.3
  %.6 = icmp sgt i32 %.5, 0
  br i1 %.6, label %while-body, label %while-exit
while-body:
  %.8 = load i32, i32* %.3
  %.9 = sub i32 %.8, 2
  store i32 %.9, i32* %.3, align 4
  %.11 = load i32, i32* %.3
  %.12 = icmp sgt i32 %.11, 0
  br i1 %.12, label %while-body, label %while-exit
while-exit:
  %.14 = load i32, i32* %.3
  %.15 = add i32 %.14, 12
  ret i32 %.15
}
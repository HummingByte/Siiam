define i32 @main(i32 %a){
entry:
  %0 = alloca i32
  store i32 %a, *i32 %0, align 4
  ret i32 2
}
define i32 @main(i32 %a){
entry:
  %_0 = alloca i32
  store i32 %a, i32* %_0, align 4
  %_2 = call i32 @test()
  ret i32 %_2
}
define i32 @test(){
entry:
  ret i32 99
}
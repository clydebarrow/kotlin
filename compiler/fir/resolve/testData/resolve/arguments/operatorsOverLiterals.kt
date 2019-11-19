fun foo(x: Int) {}
fun foo(x: Byte) {}

fun test_0() {
    foo(1)
}

fun test_1() {
    val x1 = 1 + 1
    val x2 = 1.plus(1)
    1 + 1
    127 + 1
    val x3 = 2000000000 * 4
}

fun test_2(n: Int) {
    val x = 1 + n
    val y = n + 1
}

fun Int.bar(): Int {}

fun Int.baz(): Int
fun Byte.baz(): Byte {}

fun test_3() {
    val x = 1.bar()
    val y = 1.baz()
}

fun takeByte(b: Byte) {}

fun test_4() {
    <!INAPPLICABLE_CANDIDATE!>takeByte<!>(127 + 1)
    <!INAPPLICABLE_CANDIDATE!>takeByte<!>(run { 127 + 1 })
    takeByte(1 + 1)
    takeByte(run { 1 + 1 })
    1 + 1
}
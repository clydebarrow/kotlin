// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER

fun foo(x: Int) {}
fun foo(x: Byte) {}

fun test_0() {
    foo(1)
}

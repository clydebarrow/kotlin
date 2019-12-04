// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE

interface B {
    fun c()
}

data class Pair<X, Y>(val fst: X, val snd: Y)

class A(foo: Pair<Int, (B) -> Unit>? = null)

fun main() {
    val predicate = false
    val b = true
    val a = A(
        if (b) {
            Pair(1, { baz -> baz.c() })
        } else {
            null
        }
    )
}

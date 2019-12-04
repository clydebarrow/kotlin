// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_VARIABLE -UNUSED_ANONYMOUS_PARAMETER

class Sample

fun <K> id(x: K): K = x

fun test() {
    val f1: Sample.() -> Unit = id { s: Sample -> }
    val f2: Sample.() -> Unit = id<Sample.() -> Unit> { s: Sample -> }
}

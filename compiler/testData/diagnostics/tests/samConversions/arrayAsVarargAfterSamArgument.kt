// !LANGUAGE: +NewInference +SamConversionForKotlinFunctions +SamConversionPerArgument
// SKIP_TXT

// FILE: Test.java
public class Test {
    public static String foo1(Runnable r, String... strs) {
        return null;
    }
    public String foo2(Runnable r1, Runnable r2, String... strs) {
        return null;
    }
}

// FILE: main.kt
fun main(x2: Runnable) {
    val x1 = {}

    val x3 = arrayOf<String>()

    Test.foo1({}, <!TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG!>arrayOf()<!>)
    Test.foo1({}, *arrayOf())
    Test.foo1({}, <!TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG!>x3<!>)
    Test.foo1({}, *arrayOf(""))

    Test.foo1(x1, <!TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG!>arrayOf()<!>)
    Test.foo1(x1, *arrayOf())
    Test.foo1(x2, <!TYPE_MISMATCH!>arrayOf()<!>)
    Test.foo1(x2, *arrayOf())

    Test.foo1(x1, <!TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG!>x3<!>)
    Test.foo1(x1, *x3)
    Test.foo1(x2, <!TYPE_MISMATCH!>arrayOf("")<!>)
    Test.foo1(x2, *arrayOf(""))

    Test().foo2({}, {}, <!TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG!>arrayOf()<!>)
    Test().foo2({}, {}, *arrayOf())
    Test().foo2({}, <!TYPE_MISMATCH!>x2<!>, <!TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG!>arrayOf()<!>)
    Test().foo2(x2, {}, *arrayOf())

    Test().foo2({}, {}, <!TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG!>arrayOf("")<!>)
    Test().foo2({}, {}, *x3)
    Test().foo2({}, <!TYPE_MISMATCH!>x2<!>, <!TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG!>x3<!>)
    Test().foo2(x2, {}, *arrayOf(""))
}

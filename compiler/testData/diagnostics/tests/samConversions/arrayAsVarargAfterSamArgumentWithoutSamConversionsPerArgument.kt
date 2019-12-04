// !LANGUAGE: +NewInference +SamConversionForKotlinFunctions
// SKIP_TXT

// FILE: Test.java
public class Test {
    public static String foo1(Runnable r, String... strs) {
        return null;
    }
    public String foo2(Runnable r, Runnable r, String... strs) {
        return null;
    }
}

// FILE: main.kt
fun main(x2: Runnable) {
    val x1 = {}
    val x3 = arrayOf<String>()

    Test.foo1({}, arrayOf())
    Test.foo1(<!TYPE_MISMATCH!>{}<!>, *arrayOf())
    Test.foo1(<!TYPE_MISMATCH!>{}<!>, *x3)
    Test.foo1({}, arrayOf(""))

    Test.foo1(x1, arrayOf())
    Test.foo1(<!TYPE_MISMATCH!>x1<!>, *arrayOf())
    Test.foo1(x2, <!TYPE_MISMATCH!>arrayOf()<!>)
    Test.foo1(x2, *arrayOf())

    Test.foo1(x1, x3)
    Test.foo1(<!TYPE_MISMATCH!>x1<!>, *x3)
    Test.foo1(x2, <!TYPE_MISMATCH!>arrayOf("")<!>)
    Test.foo1(x2, *arrayOf(""))

    Test().foo2({}, {}, arrayOf())
    Test().foo2(<!TYPE_MISMATCH!>{}<!>, <!TYPE_MISMATCH!>{}<!>, *arrayOf())
    Test().foo2(<!TYPE_MISMATCH!>{}<!>, x2, <!TYPE_MISMATCH!>arrayOf()<!>)
    Test().foo2(x2, <!TYPE_MISMATCH!>{}<!>, *arrayOf())

    Test().foo2({}, {}, arrayOf(""))
    Test().foo2(<!TYPE_MISMATCH!>{}<!>, <!TYPE_MISMATCH!>{}<!>, *x3)
    Test().foo2(<!TYPE_MISMATCH!>{}<!>, x2, <!TYPE_MISMATCH!>x3<!>)
    Test().foo2(x2, <!TYPE_MISMATCH!>{}<!>, *arrayOf(""))
}
// IGNORE_BACKEND_FIR: JVM_IR
// TARGET_BACKEND: JVM
// WITH_RUNTIME
// FILE: test.kt
import kotlin.test.*

fun box(): String {
    assertFailsWith<NullPointerException> { J.s().length }
    return "OK"
}

// FILE: J.java
public class J {
    public static String s() { return null; }
}

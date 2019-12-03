// FULL_JDK

val f = listOf("").<!INAPPLICABLE_CANDIDATE!>firstOrNull<!>(1)

fun <T> listOf(element: T): List<T> = java.util.Collections.singletonList(element)
fun <T> Iterable<T>.firstOrNull(predicate: (T) -> Boolean): T? = null
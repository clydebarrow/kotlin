// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_VARIABLE -ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE -UNUSED_VALUE -UNUSED_PARAMETER -UNUSED_EXPRESSION
// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (NEGATIVE)
 *
 * SPEC VERSION: 0.1-213
 * PLACE: declarations, classifier-declaration, class-declaration, abstract-classes -> paragraph 2 -> sentence 1
 * NUMBER: 4
 * DESCRIPTION: Abstract classes may contain abstract members, which should be implemented in an inner class that inherits from that abstract type
 * ISSUES: KT-27825
 */

class MainClass {
    abstract class Base1() {
        abstract val a: CharSequence
        abstract var b: CharSequence
        abstract fun foo(): CharSequence
    }

    abstract class Base2 : Base1() {
        abstract fun boo(x: Int = 10)
    }

    abstract class Base3(override val a: CharSequence) : Base1() {}
}

/*
 * TESTCASE NUMBER: 1
 * NOTE: absctract class member is not implemented in inner class
 */
class Case1 {

    abstract inner class ImplBase2() : MainClass.Base2() {
        override var b: CharSequence = ""
        override val a: CharSequence = ""
        override fun boo(x: Int) {}
    }

    inner

    <!ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED!>class ImplBase2_1<!> : ImplBase2() {
        override var b: CharSequence = ""
        override fun boo(x: Int) {}
    }
}

/*
* TESTCASE NUMBER: 2
* NOTE:absctract class member is not implemented in anonymos class
*/
class Case2() {
    abstract inner class Impl(override val a: CharSequence) : MainClass.Base3(a) {}

    fun boo() {
        val impl = <!ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED!>object<!> : Impl("a") {
            override fun foo(): CharSequence = "foo"
        }
    }
}
fun call() {
    val list : String? = "sdfknsdkfm"

    "x" <!INAPPLICABLE_CANDIDATE!>in<!> list
    "x" <!INAPPLICABLE_CANDIDATE, UNRESOLVED_REFERENCE!>!in<!> list
}

operator fun CharSequence.contains(other: CharSequence, ignoreCase: Boolean = false): Boolean = true
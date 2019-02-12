// FIX: none
// SKIP_ERRORS_AFTER

class Some {
    class Nested

    fun foo(): <caret>Nested {
        return Nested()
    }
}
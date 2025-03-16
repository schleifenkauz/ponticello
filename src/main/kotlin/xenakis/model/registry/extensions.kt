package xenakis.model.registry

fun <O : NamedObject> O.reference(): ObjectReference<O> {
    val ref = ObjectReference(this)
    return ref
}
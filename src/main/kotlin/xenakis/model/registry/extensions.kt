package xenakis.model.registry

fun <O : NamedObject> O.reference(): ObjectReference<O> {
    return ObjectReference(this)
}
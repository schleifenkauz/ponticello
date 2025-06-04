package ponticello.model.obj

fun <O: RenamableObject> O.withName(name: String): O {
    setInitialName(name)
    return this
}
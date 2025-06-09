package ponticello.model.obj

import hextant.context.Context
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject

fun <O: RenamableObject> O.withName(name: String): O {
    setInitialName(name)
    return this
}

val Context.project get() = get(currentProject)
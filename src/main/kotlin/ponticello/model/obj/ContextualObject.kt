package ponticello.model.obj

import hextant.context.Context

interface ContextualObject {
    val context: Context

    val initialized: Boolean

    fun initialize(context: Context)
}
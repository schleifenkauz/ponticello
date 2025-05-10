package ponticello.model.obj

import hextant.context.Context

interface ContextualObject {
    val context: Context

    fun initialize(context: Context)
}
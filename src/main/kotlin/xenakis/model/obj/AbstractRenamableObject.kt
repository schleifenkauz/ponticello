package xenakis.model.obj

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
abstract class AbstractRenamableObject : RenamableObject {
    @Transient
    protected var initialized = false

    protected abstract val mutableName: ReactiveVariable<String>

    final override val name: ReactiveValue<String>
        get() = mutableName

    @Transient
    lateinit var context: Context
        private set

    open fun setContext(context: Context) {
        this.context = context
    }

    override fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        setContext(context)
    }

    override fun onAdded(context: Context) {}

    override fun onRemoved() {}

    override fun rename(newName: String) {
        mutableName.now = newName
    }

    override fun toString(): String = "${javaClass.simpleName} ${name.now}"
}
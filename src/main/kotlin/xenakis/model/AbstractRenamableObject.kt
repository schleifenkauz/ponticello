package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import java.util.logging.Logger

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
        logger.fine("Initialize $this")
        initialized = true
        setContext(context)
    }

    override fun remove() {
        if (!initialized) return
        logger.fine("Remove $this")
        initialized = false
    }

    override fun rename(newName: String) {
        mutableName.now = newName
    }

    override fun toString(): String = "${javaClass.simpleName} ${name.now}"

    companion object {
        private val logger = Logger.getLogger("AbstractRenamableObject")
    }
}
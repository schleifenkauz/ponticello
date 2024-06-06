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

    override fun initialize(context: Context) {
        if (initialized) return
        logger.fine("Initialize ${this.name.now}")
        initialized = true
        this.context = context
    }

    override fun remove() {
        if (!initialized) error("Object ${this.name.now} was not initialized!")
        logger.fine("Remove ${this.name.now}")
        initialized = false
    }

    override fun rename(newName: String) {
        mutableName.now = newName
    }

    companion object {
        private val logger = Logger.getLogger("AbstractRenamableObject")
    }
}
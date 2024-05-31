package xenakis.sc.editor

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.model.RenamableObject
import java.util.logging.Logger

@Serializable
abstract class AbstractRenamableObject : RenamableObject {
    @Transient
    protected var initialized = false
        private set

    protected abstract val mutableName: ReactiveVariable<String>

    @Transient
    lateinit var context: Context
        private set

    override fun initialize(context: Context) {
        if (initialized) return
        logger.fine("Initialize ${this.name.now}")
        initialized = true
        this.context = context
    }

    override val name: ReactiveValue<String>
        get() = mutableName

    override fun rename(newName: String) {
        mutableName.now = newName
    }

    companion object {
        private val logger = Logger.getLogger("AbstractRenamableObject")
    }
}
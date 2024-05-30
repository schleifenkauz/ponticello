package xenakis.sc.editor

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.model.RenamableObject

@Serializable
abstract class AbstractRenamableObject : RenamableObject {
    protected abstract val mutableName: ReactiveVariable<String>

    @Transient
    lateinit var context: Context
        private set

    open fun initialize(context: Context) {
        this.context = context
    }

    override val name: ReactiveValue<String>
        get() = mutableName

    override fun rename(newName: String) {
        mutableName.now = newName
    }
}
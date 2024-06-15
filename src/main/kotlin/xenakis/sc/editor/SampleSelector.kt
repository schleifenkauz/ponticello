package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.Snapshot
import xenakis.model.ObjectRegistry
import xenakis.model.SampleObject
import xenakis.model.SampleObjectReference
import xenakis.model.SampleRegistry
import xenakis.ui.SampleRegistryPane

class SampleSelector(
    context: Context,
    initialValue: SampleObjectReference?
) : ObjectSelector<SampleObject, SampleObjectReference?>(context, initialValue) {
    override val isNullable: Boolean
        get() = true
    override val registry: ObjectRegistry<SampleObject>
        get() = context[SampleRegistry]

    override fun createNewObject(name: String): SampleObject? = context[SampleRegistryPane].addObject(name)

    override fun createSnapshot(): Snapshot<*> {
        TODO("Not yet implemented")
    }
}
package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.Snapshot
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.*
import xenakis.ui.SampleRegistryPane

class SampleSelector(
    context: Context,
    selected: ReactiveVariable<SampleObjectReference?>
) : ObjectSelector<SampleObject, SampleObjectReference?>(context, selected) {
    constructor(context: Context, initialValue: SampleObjectReference?) : this(context, reactiveVariable(initialValue))

    override val isNullable: Boolean
        get() = true
    override val registry: ObjectRegistry<SampleObject>
        get() = context[SampleRegistry]

    override fun createNewObject(name: String): SampleObject? = context[SampleRegistryPane].addObject(name)

    override fun createSnapshot(): Snapshot<*> = Snap()

    private class Snap : ObjectSelector.Snap<SampleObject, SampleObjectReference?>() {
        override val serializer: ObjectReference.Serializer<SampleObjectReference?>
            get() = SampleObjectReference.Serializer
    }
}
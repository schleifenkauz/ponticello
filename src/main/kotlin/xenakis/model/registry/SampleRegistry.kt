package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.reactiveVariable
import xenakis.model.obj.SampleObject
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.Identifier
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import java.io.File

@Serializable
class SampleRegistry(private val samples: MutableList<SampleObject>) : SuperColliderObjectRegistry<SampleObject>() {
    override val objects: MutableList<SampleObject>
        get() = samples
    override val objectType: String
        get() = "Sample"
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.ServerBoot

    override fun initialize(context: Context) {
        context[SampleRegistry] = this
        super.initialize(context)
    }

    fun getSample(file: File): SampleObject? = objects.find { o -> o.audioFile == file }

    fun getOrAdd(file: File): SampleObject = getSample(file) ?: run {
        val name = reactiveVariable(Identifier.truncate(file.nameWithoutExtension))
        val sample = SampleObject.create(context[currentProject], name, file)
        add(sample)
        return sample
    }

    companion object : PublicProperty<SampleRegistry> by publicProperty("SampleRegistry")
}
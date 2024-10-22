package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.SampleObject
import java.io.File

@Serializable
class SampleRegistry(private val samples: MutableList<SampleObject>) : SuperColliderObjectRegistry<SampleObject>() {
    override val objects: MutableList<SampleObject>
        get() = samples
    override val objectType: String
        get() = "Sample"

    override fun initialize(context: Context) {
        context[SampleRegistry] = this
        super.initialize(context)
    }

    fun getSample(file: File): SampleObject? = objects.find { o -> o.audioFile == file }

    override fun getDefault(name: String?): SampleObject = throw NotImplementedError()

    companion object : PublicProperty<SampleRegistry> by publicProperty("SampleRegistry")
}
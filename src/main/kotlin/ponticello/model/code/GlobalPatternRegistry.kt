package ponticello.model.code

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessage
import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.model.obj.SuperColliderObject
import ponticello.model.obj.withName
import ponticello.model.registry.CustomNamedObjectListSerializer
import ponticello.model.registry.SuperColliderObjectRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import ponticello.sc.editor.ScExprExpander

@Serializable(with = GlobalPatternRegistry.Serializer::class)
class GlobalPatternRegistry(
    override val objects: MutableList<GlobalPatternObject> = mutableListOf(),
) : SuperColliderObjectRegistry<GlobalPatternObject>() {
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override val objectType: String
        get() = "Pattern"

    override fun initialize(context: Context) {
        context[GlobalPatternRegistry] = this
        super.initialize(context)
        context[SuperColliderClient].addListener("/refresh_pattern") { _, msg -> refreshPattern(msg) }
    }

    private fun refreshPattern(msg: OSCMessage) {
        val name = msg.getArgument<String>(0, "Pattern Name") ?: return
        val pattern = getOrNull(name)
        if (pattern == null) {
            Logger.warn("Received /refresh_pattern for unknown pattern: $name", Logger.Category.OSC)
            return
        }
        pattern.sync()
    }

    @Suppress("UNCHECKED_CAST")
    object Serializer : CustomNamedObjectListSerializer<GlobalPatternObject,
            EditorRoot<ScExprExpander>,
            GlobalPatternRegistry
            >(EditorRoot.Serializer as KSerializer<EditorRoot<ScExprExpander>>) {
        override fun createList(elements: MutableList<GlobalPatternObject>): GlobalPatternRegistry =
            GlobalPatternRegistry(elements)

        override fun getContent(obj: GlobalPatternObject): EditorRoot<ScExprExpander> = obj.patternCode

        override fun createObject(name: String, content: EditorRoot<ScExprExpander>): GlobalPatternObject =
            GlobalPatternObject(content).withName(name)
    }

    companion object: PublicProperty<GlobalPatternRegistry> by publicProperty("GlobalPatternRegistry") {
        fun createDefault() = GlobalPatternRegistry()
    }
}
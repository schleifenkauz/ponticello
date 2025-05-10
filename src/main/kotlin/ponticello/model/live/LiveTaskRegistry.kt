package ponticello.model.live

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import ponticello.model.obj.SuperColliderObject
import ponticello.model.registry.CustomNamedObjectListSerializer
import ponticello.model.registry.SuperColliderObjectRegistry
import ponticello.sc.editor.CodeBlockEditor
import reaktive.value.reactiveVariable

@Serializable(with = LiveTaskRegistry.Serializer::class)
class LiveTaskRegistry(
    override val objects: MutableList<LiveTaskObject>,
) : SuperColliderObjectRegistry<LiveTaskObject>() {
    override val objectType: String
        get() = "LiveTask"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override fun initialize(context: Context) {
        context[LiveTaskRegistry] = this
        super.initialize(context)
    }

    @Suppress("UNCHECKED_CAST")
    object Serializer :
        CustomNamedObjectListSerializer<LiveTaskObject, EditorRoot<CodeBlockEditor>, LiveTaskRegistry>(
            EditorRoot.Serializer as KSerializer<EditorRoot<CodeBlockEditor>>
        ) {
        override fun createList(elements: MutableList<LiveTaskObject>): LiveTaskRegistry = LiveTaskRegistry(elements)

        override fun getContent(obj: LiveTaskObject): EditorRoot<CodeBlockEditor> = obj.code

        override fun createObject(name: String, content: EditorRoot<CodeBlockEditor>): LiveTaskObject =
            LiveTaskObject(reactiveVariable(name), content)
    }

    companion object : PublicProperty<LiveTaskRegistry> by publicProperty("LIVE_TASK_REGISTRY") {
        fun createDefault(): LiveTaskRegistry = LiveTaskRegistry(mutableListOf())
    }
}
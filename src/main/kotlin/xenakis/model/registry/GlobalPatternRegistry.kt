package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import reaktive.value.reactiveVariable
import xenakis.model.obj.GlobalPatternObject
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.editor.ScExprExpander

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
            GlobalPatternObject(reactiveVariable(name), content)
    }

    companion object: PublicProperty<GlobalPatternRegistry> by publicProperty("GlobalPatternRegistry") {
        fun createDefault() = GlobalPatternRegistry()
    }
}
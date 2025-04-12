package xenakis.model.registry

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Serializable
import reaktive.value.reactiveVariable
import xenakis.model.obj.GlobalPatternObject
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.editor.CodeBlockEditor

@Serializable(with = GlobalPatternRegistry.Serializer::class)
class GlobalPatternRegistry(
    override val objects: MutableList<GlobalPatternObject> = mutableListOf(),
) : SuperColliderObjectRegistry<GlobalPatternObject>() {
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override val objectType: String
        get() = "Pattern"

    override fun initialize(context: Context) {
        super.initialize(context)
    }

    object Serializer :
        CustomNamedObjectListSerializer<GlobalPatternObject, EditorRoot<CodeBlockEditor>, GlobalPatternRegistry>(
            kotlinx.serialization.serializer()
        ) {
        override fun createList(elements: MutableList<GlobalPatternObject>): GlobalPatternRegistry =
            GlobalPatternRegistry(elements)

        override fun getContent(obj: GlobalPatternObject): EditorRoot<CodeBlockEditor> = obj.patternCode

        override fun createObject(
            name: String,
            content: EditorRoot<CodeBlockEditor>,
        ): GlobalPatternObject = GlobalPatternObject(reactiveVariable(name), content)
    }

    companion object {
        fun createDefault() = GlobalPatternRegistry()
    }
}
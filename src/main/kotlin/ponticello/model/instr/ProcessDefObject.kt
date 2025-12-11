package ponticello.model.instr

import bundles.set
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.ColorSerializer
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.impl.randomColor
import ponticello.model.obj.AbstractSuperColliderObject
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectRegistry
import ponticello.model.registry.reference
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class ProcessDefObject(
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    override val parameters: ParameterDefList,
    val body: EditorRoot<@Contextual CodeBlockEditor> = EditorRoot(CodeBlockEditor().defaultState()),
) : ConfigurableInstrumentObject, AbstractSuperColliderObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override fun superColliderName(objectName: String) = "~proc_${objectName}"

    override val registry: ObjectRegistry<*>
        get() = context[InstrumentRegistry]

    override fun supports(type: ParameterType): Boolean = true

    override fun copy(): ProcessDefObject = ProcessDefObject(
        color.copy(),
        ParameterDefList(parameters.mapTo(mutableListOf()) { p -> p.copy().withName(p.name.now) }),
        body.clone(context)
    )

    override fun ScWriter.sync() {
        createObject()
    }

    override fun ScWriter.createObject() {
        val subst = body.editor.result.now.transform<ParameterReference> { ref ->
            Identifier("inst").send("getControlValue", SymbolLiteral(ref.parameter.getName()))
        }.substitute(
            mapOf(
            "time" to { Identifier("inst").send("current_time") }
        ))
        val defaultValueMap = parameters.filter { p -> p.spec.now is NumericalControlSpec }
            .joinToString(", ", "(", ")") { param ->
                val spec = param.spec.now as NumericalControlSpec
                "${param.name.now}: ${spec.defaultValue.text}"
            }
        appendBlock("$superColliderName = RoutineInstrument($defaultValueMap)") {
            append("arg inst, duration")
            subst.code(this, context)
        }
    }

    override fun sync() {
        context[SuperColliderClient].run { writer.sync() }
        Logger.confirm("Synchronized ProcessDef '${name.now}'", Logger.Category.Instruments)
    }

    override fun ScWriter.freeObject() {
        +"$superColliderName = nil"
    }

    override fun initialize(context: Context) {
        if (initialized) return
        val myContext = context.extend {
            set(SelectionDistributor, SelectionDistributor.newInstance())
        }
        super.initialize(myContext)
        parameters.initialize(myContext)
        body.initialize(myContext)
    }

    override fun onRename(oldName: String, newName: String) {
        sync()
    }

    override fun instrumentReference() = InstrumentReference.UserDefined(this.reference())

    companion object {
        fun newEmpty(name: String) = ProcessDefObject(
            color = reactiveVariable(randomColor()),
            parameters = ParameterDefList(),
            body = EditorRoot(CodeBlockEditor().defaultState())
        ).withName(name)

        fun unresolved() = newEmpty("<unresolved>")
    }
}
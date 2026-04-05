package ponticello.model.score.controls

import bundles.getOrNull
import bundles.set
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.extend
import hextant.serial.EditorRoot
import kotlinx.serialization.Transient
import ponticello.model.ctx.ParameterControlVariable
import ponticello.model.ctx.PonticelloContext
import ponticello.model.ctx.Scope
import ponticello.model.instr.ParameterizedObject
import ponticello.model.midi.ParameterizedMidiInstrument
import ponticello.sc.*
import ponticello.sc.editor.ScExprExpander
import reaktive.event.unitEvent
import reaktive.value.now

sealed class CodeControl : ParameterControl() {
    @Transient
    val update = unitEvent()

    abstract val expr: EditorRoot<ScExprExpander>

    override fun initialize(context: Context, namedControl: NamedParameterControl) {
        if (initialized) return
        super.initialize(context, namedControl)
        if (!(expr.editor.isInitialized)) {
            val myContext = context.extend {
                set(UndoManager, context[UndoManager]/*.createSubManager()*/)
                set(PonticelloContext, PonticelloContext.Control(namedControl))
                val parent = context.getOrNull(Scope)
                val scope = Scope.fromList(namedControl.controls, parent, ::ParameterControlVariable)
                scope.defineBoundVariables(namedControl)
                set(Scope, scope)
            }
            expr.initialize(myContext)
        }
    }

    protected open fun Scope.defineBoundVariables(namedControl: NamedParameterControl) {}

    companion object {
        @JvmStatic
        protected fun substituteParameterReferences(
            expr: ScExpr, obj: ParameterizedObject,
            references: MutableSet<String>?,
            substitute: (String) -> ScExpr
        ): ScExpr {
            val map = obj.controls.associateTo(mutableMapOf()) { ctrl ->
                val parameter = ctrl.name.now
                parameter to {
                    references?.add(parameter)
                    substitute(parameter)
                }
            }
            if (obj is ParameterizedMidiInstrument) {
                for (param in setOf("pitch", "velocity")) {
                    map[param] = { Identifier("inst").send("get", SymbolLiteral(param)) }
                }
            }
            return expr.substitute(map)
        }
    }
}
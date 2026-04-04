package ponticello.model.code

import bundles.set
import com.illposed.osc.OSCMessageEvent
import fxutils.drag.TypedDataFormat
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.model.ctx.PonticelloContext
import ponticello.model.ctx.Scope
import ponticello.model.obj.AbstractSuperColliderObject
import ponticello.model.obj.project
import ponticello.model.obj.withName
import ponticello.model.project.OSC_HOOKS
import ponticello.model.project.get
import ponticello.sc.DisabledExpr
import ponticello.sc.client.ScWriter
import ponticello.sc.client.getArgument
import ponticello.sc.client.run
import ponticello.sc.editor.ScFunctionEditor
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveInt
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class OSCHookObject(
    val function: EditorRoot<@Contextual ScFunctionEditor>,
    private val enabled: ReactiveVariable<Boolean> = reactiveVariable(true),
) : AbstractSuperColliderObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override fun superColliderName(objectName: String): String = "~osc_${objectName}"

    @Transient
    private val _events = mutableListOf<Event>()
    val events: List<Event> get() = _events

    @Transient
    private val _eventCount = reactiveVariable(0)
    val eventCount: ReactiveInt get() = _eventCount

    override val registry: OSCHookRegistry
        get() = context.project[OSC_HOOKS]

    override fun initialize(context: Context) {
        super.initialize(context)
        function.initialize(context.extend {
            set(SelectionDistributor, SelectionDistributor.newInstance())
            set(PonticelloContext, PonticelloContext.OSCHook(this@OSCHookObject))
            set(Scope, Scope.createEmpty())
        })
    }

    val isEnabled: ReactiveBoolean
        get() = enabled

    fun toggleEnabled() {
        enabled.now = !enabled.now
        client.run {
            if (enabled.now) +"$superColliderName.enable"
            else +"$superColliderName.disable"
        }
    }

    override fun ScWriter.createObject() {
        val path = name.now
        val func = function.editor.result.now
        appendBlock("$superColliderName = OSCFunc(", endLine = null) {
            +"arg msg, time, addr, recvPort"
            val vars = func.parameters + func.body.variables
            if (vars.isNotEmpty()) {
                +"var ${vars.joinToString(", ") { p -> p.text }}"
            }
            for ((i, p) in func.parameters.withIndex()) {
                +"${p.text} = msg[${i + 1}]"
            }
            appendBlock("try", endLine = null) {
                for (statement in func.body.statements) {
                    statement.code(this, context)
                    if (statement !is DisabledExpr) appendLine(";")
                    else appendLine()
                }
            }
            +"{ |error| error.reportError }"
            +"Ponticello.sendMsg(\\osc_hook, '$path', time, addr.ip, addr.port, *msg[1..])"
        }
        appendLine(", '$path').fix;")
        if (!enabled.now) +"$superColliderName.disable"
    }

    override fun onRename(oldName: String, newName: String) {
        sync()
    }

    fun addEvent(ev: OSCMessageEvent) {
        val timestamp = ev.message.getArgument<Float>(1, "timestamp") ?: return
        val hostname = ev.message.getArgument<String>(2, "hostname") ?: return
        val port = ev.message.getArgument<Int>(3, "port") ?: return
        val arguments = ev.message.arguments.drop(4).map { it.toString() }
        val event = Event(timestamp, hostname, port, arguments)
        _events.add(event)
        _eventCount.now += 1
    }

    fun resetEvents() {
        _events.clear()
        _eventCount.now = 0
    }

    data class Event(
        val timestamp: Float,
        val hostname: String,
        val port: Int,
        val arguments: List<String>
    )

    companion object {
        val DATA_FORMAT = TypedDataFormat<OSCHookObject>("ponticello:osc_hook")

        fun create(name: String) = OSCHookObject(EditorRoot(ScFunctionEditor().defaultState())).withName(name)
    }
}
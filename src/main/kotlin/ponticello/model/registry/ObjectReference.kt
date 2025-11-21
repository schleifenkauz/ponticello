package ponticello.model.registry

import hextant.context.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ponticello.impl.Logger
import ponticello.model.obj.NamedObject
import ponticello.model.obj.SuperColliderObject
import ponticello.model.obj.superColliderName
import ponticello.model.score.ScoreObject
import ponticello.sc.ScExpr
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveValue

@Serializable(with = ObjectReference.Serializer::class)
class ObjectReference<O : NamedObject>(private var _name: String) : ScExpr, java.io.Serializable {
    @Transient
    private var obj: O? = null

    @Transient
    lateinit var isResolved: ReactiveBoolean
        private set

    override val isValid: Boolean
        get() = isResolved.now

    constructor(obj: O) : this(obj.name.now) {
        this.obj = obj
        isResolved = obj.isAdded
    }

    fun setUnresolved() {
        obj = null
        isResolved = reactiveValue(false)
    }

    fun resolve(registry: List<O>): O? {
        if (obj != null) return obj as O
        if (_name == "<none>") {
            isResolved = reactiveValue(false)
            return null
        }
        obj = registry.find { obj -> obj.name.now == _name }
        if (obj != null) {
            isResolved = obj!!.isAdded
        } else {
            val objectType = if (registry is ObjectList) registry.objectType else "Object"
            Logger.warn("$objectType '$_name' not found", Logger.Category.Registries)
            isResolved = reactiveValue(false)
        }
        return obj
    }

    fun get(): O? = obj

    fun force(): O = obj ?: error("ObjectReference ${getName()}is not resolved")

    fun getName() = obj?.name?.now ?: _name

    val name get() = obj?.name ?: reactiveValue(_name)

    override fun toString(): String = if (_name == NONE) NONE else "#$_name"

    val superColliderName: String
        get() {
            val obj = get()
            return when {
                _name == NONE -> "<none>"
                obj == null -> "<unresolved: $_name>"
                obj is SuperColliderObject -> obj.superColliderName
                else -> error("$obj has no SuperCollider name")
            }
        }

    override fun code(writer: ScWriter, context: Context) {
        val obj = get()
        if (obj is ScoreObject) writer.append("\"${obj.name.now}\"")
        else writer.append(superColliderName)
    }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is ObjectReference<*> -> false
        obj != null -> obj == other.obj
        else -> _name == other._name
    }

    override fun hashCode(): Int = when {
        obj != null -> obj.hashCode()
        else -> _name.hashCode()
    }

    object Serializer : KSerializer<ObjectReference<*>> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<String>()

        override fun serialize(encoder: Encoder, value: ObjectReference<*>) {
            encoder.encodeString(value.getName())
        }

        override fun deserialize(decoder: Decoder): ObjectReference<*> {
            val name = decoder.decodeString()
            return ObjectReference<NamedObject>(name)
        }
    }

    companion object {
        const val NONE = "<none>"

        fun <O : NamedObject> none() = ObjectReference<O>(NONE).also { it.setUnresolved() }
    }
}
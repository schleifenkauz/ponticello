package xenakis.model.registry

import hextant.context.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.Logger
import xenakis.model.obj.GroupObject
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter

@Serializable(/*with = ObjectReference.Serializer::class*/) //TODO
class ObjectReference<O : NamedObject>(private var _name: String) : ScExpr {
    private var obj: O? = null

    lateinit var isResolved: ReactiveBoolean
        private set

    constructor(obj: O) : this(obj.name.now) {
        resolve(obj)
    }

    private fun resolve(obj: O) {
        this.obj = obj
        isResolved = obj.isAdded
    }

    fun resolve(registry: ObjectRegistry<O>): O? {
        if (obj != null) return obj as O
        if (_name == "<none>") return null
        try {
            resolve(registry.get(_name))
        } catch (ex: NoSuchElementException) {
            Logger.severe("${registry.objectType} '$_name' not found", Logger.Category.Registries)
            obj = null
            isResolved = reactiveValue(false)
        }
        return obj
    }

    fun get(): O? = obj?.takeIf { isResolved.now }

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
                obj is GroupObject -> obj.superColliderName
                else -> error("$obj has no SuperCollider name")
            }
        }

    override fun code(writer: ScWriter, context: Context) {
        writer.append(superColliderName)
    }

    @Suppress("unused") //is needed because [ObjectReference] has type parameter <O>
    class Serializer<O : NamedObject>(val serializer: KSerializer<O>) : KSerializer<ObjectReference<O>> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<String>()

        override fun serialize(encoder: Encoder, value: ObjectReference<O>) {
            encoder.encodeString(value.getName())
        }

        override fun deserialize(decoder: Decoder): ObjectReference<O> {
            val name = decoder.decodeString()
            return ObjectReference(name)
        }
    }

    companion object {
        const val NONE = "<none>"

        fun <O: NamedObject> none() = ObjectReference<O>(NONE)
    }
}
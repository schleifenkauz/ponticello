package xenakis.model.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import reaktive.value.now
import xenakis.model.Logger
import kotlin.reflect.KClass
import kotlin.reflect.cast

@Serializable(with = ObjectReference.Serializer::class)
class ObjectReference(private var name: String) {
    @Transient
    private var obj: NamedObject? = null

    constructor(obj: NamedObject) : this(obj.name.now) {
        this.obj = obj
    }

    fun <O : NamedObject> get(cls: KClass<O>): O = when {
        obj == null -> error("Object $name not resolved!")
        !cls.isInstance(obj) -> error("Object $name was not resolved to an object of $cls")
        else -> cls.cast(obj)
    }

    inline fun <reified O : NamedObject> get(): O = get(O::class)

    fun getName() = obj?.name?.now ?: name

    @Suppress("UNCHECKED_CAST")
    fun <O : NamedObject> resolve(registry: ObjectRegistry<O>): O {
        if (obj != null) return obj as O
        try {
            obj = registry.get(name)
        } catch (ex: NoSuchElementException) {
            Logger.severe("${registry.objectType} '$name' not found", Logger.Category.Registries)
            obj = registry.getDefault(name)
        }
        return obj as O
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObjectReference) return false

        return if (obj != null) obj == other.obj
        else name == other.name
    }

    override fun hashCode(): Int {
        return if (obj != null) obj.hashCode() else name.hashCode()
    }

    override fun toString(): String = "#$name"

    class Serializer : KSerializer<ObjectReference> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<String>()

        override fun serialize(encoder: Encoder, value: ObjectReference) {
            val obj = value.get(NamedObject::class)
            encoder.encodeString(obj.name.now)
        }

        override fun deserialize(decoder: Decoder): ObjectReference {
            val name = decoder.decodeString()
            return ObjectReference(name)
        }
    }
}
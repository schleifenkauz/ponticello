package xenakis.model

import bundles.set
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import hextant.undo.UndoManager
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import reaktive.value.now
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.project.component
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.code
import xenakis.sc.editor.CodeBlockEditor

class ScriptObject(val root: EditorRoot<@Contextual CodeBlockEditor>, val type: Type) : AbstractContextualObject() {
    override fun initialize(context: Context) {
        super.initialize(context)
        root.initialize(context.extend {
            set(SelectionDistributor, SelectionDistributor.newInstance())
            set(UndoManager, UndoManager.newInstance())
        })
        val client = context[SuperColliderClient]
        when (type) {
            Type.AFTER_BOOT -> client.onServerBooted {
                executeContents(client)
            }

            Type.SERVER_TREE -> client.onTreeCleared {
                executeContents(client)
            }

            else -> {}
        }
    }

    fun executeContents(client: SuperColliderClient) {
        val code = root.editor.result.now.code(context)
        client.run(code)
    }

    class Serializer(val type: Type) : KSerializer<ScriptObject> {
        private val codeSerializer = EditorRoot.Serializer

        override val descriptor: SerialDescriptor
            get() = codeSerializer.descriptor

        @Suppress("UNCHECKED_CAST")
        override fun deserialize(decoder: Decoder): ScriptObject {
            val root = decoder.decodeSerializableValue(codeSerializer)
            return ScriptObject(root as EditorRoot<CodeBlockEditor>, type)
        }

        override fun serialize(encoder: Encoder, value: ScriptObject) {
            encoder.encodeSerializableValue(codeSerializer, value.root)
        }
    }

    @Serializable
    enum class Type {
        PLAYGROUND,
        BEFORE_BOOT,
        AFTER_BOOT,
        SERVER_TREE;

        override fun toString(): String = when (this) {
            PLAYGROUND -> "Playground"
            BEFORE_BOOT -> "Before Boot"
            AFTER_BOOT -> "After Boot"
            SERVER_TREE -> "Server Tree"
        }

        val component = component<ScriptObject>(
            name = this.name.lowercase(),
            default = { ScriptObject(EditorRoot<@Contextual CodeBlockEditor>(CodeBlockEditor().defaultState()), this) },
            serializer = Serializer(this)
        )
    }

    companion object {
        fun component(type: Type) = component(
            name = type.name.lowercase(),
            default = { ScriptObject(EditorRoot(CodeBlockEditor().defaultState()), type) },
            serializer = Serializer(type)
        )
    }
}
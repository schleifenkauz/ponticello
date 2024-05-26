package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.makeRoot
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.sc.ParameterDef
import xenakis.sc.editor.ParameterDefExpander
import xenakis.sc.editor.ParameterDefListEditor

@Serializable
class Settings(
    val defaultParametersDefs: ParameterDefListEditor,
) {
    init {
        defaultParametersDefs.makeRoot()
    }

    fun getDefaultControlSpec(name: String) = defaultParametersDefs.result.now.find { p -> p.name.text == name }?.spec

    companion object : PublicProperty<Settings> by publicProperty("SETTINGS") {
        fun createDefault(context: Context): Settings {
            val settings = Settings(ParameterDefListEditor(context))
            context.withoutUndo {
                for (param in ParameterDef.defaults) {
                    val editor = ParameterDefExpander(context, param)
                    settings.defaultParametersDefs.addLast(editor)
                }
            }
            return settings
        }
    }
}
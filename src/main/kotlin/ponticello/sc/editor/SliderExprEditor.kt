package ponticello.sc.editor

import hextant.core.EditorView
import hextant.core.editor.AbstractEditor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import ponticello.impl.*
import ponticello.sc.DecimalLiteral
import ponticello.sc.NumericalControlSpec
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class SliderExprEditor(
    val spec: ReactiveVariable<NumericalControlSpec> = reactiveVariable(NumericalControlSpec.DEFAULT),
    val value: ReactiveVariable<Decimal> = reactiveVariable(spec.now.defaultValue.get()),
) : ScExprEditor<DecimalLiteral>, AbstractEditor<DecimalLiteral, EditorView>() {
    constructor(spec: NumericalControlSpec, value: Decimal = spec.defaultValue.get()) : this(
        reactiveVariable(spec), reactiveVariable(value)
    )

    override val result: ReactiveValue<DecimalLiteral> = value.map(::DecimalLiteral)

    override fun serialize(): JsonElement = buildJsonObject {
        put("spec", json.encodeToJsonElement(spec.now))
        put("value", JsonPrimitive(value.now.toString()))
    }

    override fun deserialize(element: JsonElement) {
        require(element is JsonObject)
        val specification = element.getSerializableValue<NumericalControlSpec>("spec")
        if (specification == null) {
            Logger.warn(
                "Could not read specification for SliderExprEditor from $element",
                Logger.Category.Serialization
            )
        } else {
            spec.now = specification
        }
        val strValue = element.getString("value")
        if (strValue == null) {
            Logger.warn(
                "SliderExprEditor: Property 'value' missing from $element",
                Logger.Category.Serialization
            )
        } else {
            val decValue = strValue.parseDecimal()
            if (decValue == null) {
                Logger.warn(
                    "SliderExprEditor: Could not parse value '$strValue' for $element",
                    Logger.Category.Serialization
                )
            } else {
                value.now = decValue
            }
        }
    }
}
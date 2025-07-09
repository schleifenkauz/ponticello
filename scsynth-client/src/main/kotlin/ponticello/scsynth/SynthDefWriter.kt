package ponticello.scsynth

import java.io.DataOutputStream

class SynthDefWriter(private val output: DataOutputStream) {
    private fun writeString(str: String) {
        output.writeByte(str.length)
        output.writeBytes(str)
    }

    private fun writeConstants(constants: List<Float>) {
        output.writeInt(constants.size)
        for (const in constants) {
            output.writeFloat(const)
        }
    }

    private fun writeParameters(parameters: List<Parameter>) {
        val parameterValues = parameters.flatMap { p -> p.defaultValues }
        output.writeInt(parameterValues.size)
        for (value in parameterValues) output.writeFloat(value)
        output.writeInt(parameters.size)

        for (param in parameters) {
            writeString(param.name)
        }
    }

    private fun writeUGenSpecs(ugens: List<UGenSpec>) {
        output.writeInt(ugens.size)
        for (spec in ugens) {
            writeString(spec.className)
            output.writeByte(spec.rate.ordinal)
            output.writeInt(spec.inputs.size)
            output.writeInt(spec.outputRates.size)
            output.writeShort(spec.specialIndex)

            for (input in spec.inputs) {
                when (input) {
                    is ConstantInputSpec -> {
                        output.writeInt(-1)
                        output.writeInt(input.index)
                    }

                    is UGenInputSpec -> {
                        output.writeInt(input.index)
                        output.writeInt(input.outputIndex)
                    }
                }
            }

            spec.outputRates.forEach { rate -> output.writeByte(rate.ordinal) }
        }
    }

    private fun writeVariants(variants: List<Variant>) {
        output.writeInt(variants.size)
        for (variant in variants) {
            writeString(variant.name)
            for (value in variant.parameterValues) {
                output.writeFloat(value)
            }
        }
    }

    private fun writeSynthDef(synthDef: CompiledSynthDef) {
        writeString(synthDef.name)
        writeConstants(synthDef.constants)
        writeParameters(synthDef.parameters)
        writeUGenSpecs(synthDef.ugens)
        writeVariants(synthDef.variants)
    }

    fun write(defs: List<CompiledSynthDef>) {
        val idBytes = ID.toByteArray(Charsets.UTF_8)
        output.write(idBytes)
        output.writeInt(VERSION)
        output.writeShort(defs.size)
        for (def in defs) writeSynthDef(def)
    }
}
package ponticello.scsynth

import java.io.DataInputStream

class SynthDefParser(private val input: DataInputStream) {
    fun parse(): List<CompiledSynthDef> {
        val idBytes = ByteArray(4)
        input.read(idBytes)
        val id = idBytes.toString(Charsets.UTF_8)
        require(id == "SCgf") { "Invalid id: $id" }
        val version = input.readInt()
        require(version == VERSION) { "Invalid version: $version" }
        val numDefs = input.readShort().toInt()
        return List(numDefs) { parseSynthDef() }
    }

    private fun parseSynthDef(): CompiledSynthDef {
        val name = readPString()
        val nConstants = input.readInt()
        val constants = List(nConstants) { input.readFloat() }
        val parameters = parseParameters()
        val ugens = List(input.readInt()) { parseUgenSpec(constants) }
        val variants = if (input.available() < 4) emptyList() else List(input.readInt()) { parseVariant() }
        return CompiledSynthDef(name, constants, parameters, ugens, variants)
    }

    private fun parseParameters(): List<Parameter> {
        val nParameterValues = input.readInt()
        val parameterDefaults = List(nParameterValues) { input.readFloat() }
        val nParameters = input.readInt()
        val parameters = List(nParameters) {
            val name = readPString()
            val index = input.readInt()
            Parameter(index, name)
        }
        for ((p1, p2) in parameters.zipWithNext()) {
            p1.defaultValues = parameterDefaults.slice(p1.index until p2.index)
        }
        if (parameters.isNotEmpty()) {
            parameters.last().defaultValues = parameterDefaults.drop(parameters.last().index)
        }
        return parameters
    }

    private fun parseVariant(): Variant {
        val name = readPString()
        val nParameterValues = input.readInt()
        val parameterValues = List(nParameterValues) { input.readFloat() }
        return Variant(name, parameterValues)
    }

    private fun parseUgenSpec(constants: List<Float>): UGenSpec {
        val className = readPString()
        val rate = readRate()
        val nInputs = input.readInt()
        val nOutputs = input.readInt()
        val specialIndex = input.readShort()
        val inputs = List(nInputs) {
            val index1 = input.readInt()
            val index2 = input.readInt()
            InputSpec.fromIndices(index1, index2, constants)
        }
        val outputs = List(nOutputs) { readRate() }
        return UGenSpec(className, rate, specialIndex.toInt(), inputs, outputs)
    }

    private fun readRate(): Rate {
        val value = input.readByte().toInt()
        return Rate.entries[value]
    }

    private fun readPString(): String {
        val length = input.readUnsignedByte()
        val bytes = ByteArray(length)
        input.read(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
}
package xenakis.model

import kotlinx.serialization.Serializable

@Serializable
class VSTPlugin private constructor(
    private val name: String,
    private val pluginName: String,
    private val presetName: String
)
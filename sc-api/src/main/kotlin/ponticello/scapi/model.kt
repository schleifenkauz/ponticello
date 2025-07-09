package ponticello.scapi

data class SynthDef(val name: String, val ugenGraph: () -> UGen)
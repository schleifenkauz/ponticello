package ponticello.scapi

import ponticello.scsynth.Rate

sealed class UGen

data class ConstantUGen(val value: Float) : UGen()

data class RegularUGen(val className: String, val rate: Rate, val inputs: List<UGen>) : UGen()

enum class BinaryOperator { ADD, SUB, MUL, DIV }

enum class UnaryOperator { ABS, SIN, COS, TAN, EXP, LOG, POW }

data class BinaryOpUGen(val operator: BinaryOperator, val left: UGen, val right: UGen) : UGen()

data class UnaryOpUgen(val operator: UnaryOperator, val input: UGen) : UGen()

data class ControlUGen(val parameterName: String, val defaultValues: List<Float>) : UGen()
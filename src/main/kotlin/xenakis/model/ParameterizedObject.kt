package xenakis.model

interface ParameterizedObject {
    fun getParameter(name: String): ParameterDefObject
}
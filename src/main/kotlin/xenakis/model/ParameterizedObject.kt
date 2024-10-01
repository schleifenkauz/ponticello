package xenakis.model

interface ParameterizedObject {
    fun getParameter(name: String): ParameterDefObject?

    fun hasParameter(name: String): Boolean
}
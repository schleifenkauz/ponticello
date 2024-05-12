package xenakis.impl

interface SuperColliderContext {
    fun postAsync(command: String)

    fun postAsync(writeCode: ScWriter.() -> Unit): Unit = postAsync(code(writeCode))
}
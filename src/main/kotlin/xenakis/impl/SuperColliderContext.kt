package xenakis.impl

interface SuperColliderContext {
    fun run(command: String)

    fun run(writeCode: ScWriter.() -> Unit): Unit = run(code(writeCode))
}
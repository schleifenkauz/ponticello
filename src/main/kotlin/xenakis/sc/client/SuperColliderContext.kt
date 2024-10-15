package xenakis.sc.client

interface SuperColliderContext {
    fun run(command: String)

    fun run(writeCode: ScWriter.() -> Unit)
}
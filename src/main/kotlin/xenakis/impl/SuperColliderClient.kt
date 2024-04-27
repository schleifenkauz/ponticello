package xenakis.impl

interface SuperColliderClient {
    fun post(command: String): String
    fun waitForExit()
    fun quit()

    val String.e: String get() = post(this)
    val String.d: Double get() = post(this).toDouble()
    val String.i: Int get() = post(this).toInt()
}
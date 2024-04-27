package xenakis.impl

import java.io.File
import kotlin.concurrent.thread

class ShellSuperColliderClient(private val process: Process) : SuperColliderClient {
    private val console = process.outputStream

    private var inheritIO = false
    private var hasReadStartupMessage = false

    override fun post(command: String): String = post(command, printCommand = false)

    fun post(command: String, printCommand: Boolean): String {
        writeCommand(command)
        return readResult(command, printCommand).also { println("$command -> $it") }
    }

    private fun writeCommand(command: String) {
        console.write(command.toByteArray(), 0, command.length)
        console.write('\n'.code)
        console.flush()
    }

    private fun readResult(command: String, printCommand: Boolean): String {
        if (!hasReadStartupMessage) readStartupMessage()
        Thread.sleep(50)
        while (process.inputStream.available() == 0) Thread.sleep(10)
        val buf = StringBuilder()
        while (true) {
            val bytes = process.inputStream.readNBytes(process.inputStream.available())
            val str = String(bytes)
            buf.append(str)
            if (str.contains("sc3>")) break
            Thread.sleep(10)
        }
        val message = String(buf)
        if (inheritIO) {
            if (printCommand) print(message)
            else print(message.removePrefix(command + "\r\n"))
        }
        val resultStart = (message.indexOf("\n-> ").takeIf { it != -1 } ?: -4) + 4
        val resultEnd = message.indexOf("\r\nsc3>").takeIf { it != -1 } ?: message.length
        return message.substring(resultStart, resultEnd)
    }

    private fun readStartupMessage() {
        while (true) {
            if (process.inputStream.available() != 0) {
                val msg = String(process.inputStream.readNBytes(process.inputStream.available()))
                if (inheritIO) print(msg)
                if (msg.endsWith("sc3> ")) break
            }
        }
        hasReadStartupMessage = true
    }

    fun inheritIO() {
        inheritIO = true
        if (!hasReadStartupMessage) readStartupMessage()
        thread {
            while (true) {
                val command = readlnOrNull() ?: break
                command.e
            }
        }
    }

    override fun waitForExit() {
        thread {
            val exitCode = process.waitFor()
            println("sclang exited with code $exitCode")
        }
    }

    override fun quit() {
        process.destroy()
    }

    fun executeFile(file: File, varName: String = "x"): String {
        return "$varName = this.executeFile(\"${file.absolutePath.replace('\\', '/')}\")".e
    }

    companion object {
        private const val SC_HOME = "C:\\Program Files\\SuperCollider-3.13.0"

        private const val SC_LANG = "$SC_HOME\\sclang.exe"

        fun start(): ShellSuperColliderClient {
            val rt = Runtime.getRuntime()
            val process = rt.exec(SC_LANG)
            rt.addShutdownHook(Thread {
                rt.exec("taskkill /IM sclang.exe /F")
                rt.exec("taskkill /IM scsynth.exe /F")
            })
            return ShellSuperColliderClient(process)
        }
    }
}
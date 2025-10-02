package ponticello.impl

import java.io.File
import java.io.IOException


abstract class Platform {
    fun runCommand(vararg command: String) {
        ProcessBuilder(*command).start()
    }

    fun isCommandAvailable(command: String): Boolean {
        val checkCmd = if (this is Windows) "where" else "which"
        val process = ProcessBuilder(checkCmd, command).start()
        return try {
            process.inputStream.bufferedReader().use { reader ->
                !reader.readLine().isNullOrEmpty()
            }
        } catch (e: IOException) {
            false
        }
    }

    abstract fun openDirectory(path: File)

    abstract fun openTerminal(path: File)

    object Windows : Platform() {
        override fun openDirectory(path: File) {
            runCommand("explorer.exe /select,$path")
        }

        override fun openTerminal(path: File) {
            runCommand("cmd.exe", "/k", "cd /d $path")
        }
    }

    object Mac : Platform() {
        override fun openDirectory(path: File) {
            runCommand(
                "osascript",
                "-e", "tell application \"Terminal\" to do script \"cd '$path'\""
            )
        }

        override fun openTerminal(path: File) {
            runCommand("open", path.absolutePath)
        }
    }

    object Linux : Platform() {
        private val terminals = listOf("gnome-terminal", "konsole", "xfce4-terminal", "xterm")

        override fun openDirectory(path: File) {
            runCommand("xdg-open", path.absolutePath)
        }

        override fun openTerminal(path: File) {
            var launched = false
            for (term in terminals) {
                try {
                    runCommand(term, "--working-directory=$path")
                    launched = true
                    break
                } catch (e: IOException) {
                    // try next
                }
            }
            if (!launched) {
                throw IOException("No known terminal found")
            }
        }
    }

    data class Unknown(val osName: String) : Platform() {
        override fun openDirectory(path: File) {
            throw UnsupportedOperationException("Unknown platform '$osName'.")
        }

        override fun openTerminal(path: File) {
            throw UnsupportedOperationException("Unknown platform '$osName'.")
        }
    }

    companion object {
        private val instance = run {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("win") -> Windows
                os.contains("mac") -> Mac
                os.contains("nix") || os.contains("nux") -> Linux
                else -> Unknown(os)
            }
        }

        fun get() = instance
    }
}
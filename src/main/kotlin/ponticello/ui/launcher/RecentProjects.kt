package ponticello.ui.launcher

import java.io.File
import java.util.prefs.Preferences

class RecentProjects {
    private val preferences = Preferences.userNodeForPackage(PonticelloApp::class.java)

    private val recentProjects = loadRecentProjects()

    private fun loadRecentProjects(): MutableList<File> {
        val str = preferences.get("recentProjects", "")
        val paths = str.split(File.pathSeparator).filter { it.isNotEmpty() }
        return paths.mapTo(mutableListOf(), ::File)
    }

    fun push(path: File) = updateRecentProjects {
        preferences.put("activeProject", path.absolutePath)
        recentProjects.remove(path)
        recentProjects.add(0, path)
    }

    fun removeFromRecentProjects(proj: File) {
        if (activeProject() == proj) {
            clearActiveProject()
        }
        updateRecentProjects {
            recentProjects.remove(proj)
        }
    }

    fun clearActiveProject() {
        preferences.put("activeProject", "")
    }

    private inline fun updateRecentProjects(action: MutableList<File>.() -> Unit) {
        recentProjects.action()
        val str = recentProjects.joinToString(File.pathSeparator)
        preferences.put("recentProjects", str)
    }

    fun list(): List<File> = recentProjects

    fun activeProject(): File? = preferences.get("activeProject", null)?.let(::File)?.takeIf { it.exists() }
}
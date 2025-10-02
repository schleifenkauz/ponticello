package ponticello.ui.launcher

import java.io.File

sealed interface OpenProjectOption {
    fun openProject(launcher: PonticelloLauncher)

    data object CreateNew : OpenProjectOption {
        override fun openProject(launcher: PonticelloLauncher) {
            launcher.createNewProject()
        }
    }

    data object OpenFromFileSystem : OpenProjectOption {
        override fun openProject(launcher: PonticelloLauncher) {
            launcher.openProject()
        }
    }

    data object CloneFromRepository : OpenProjectOption {
        override fun openProject(launcher: PonticelloLauncher) {
            launcher.cloneRepository()
        }
    }

    data class RecentProject(val directory: File, val name: String) : OpenProjectOption {
        override fun openProject(launcher: PonticelloLauncher) {
            launcher.openProject(directory)
        }
    }

    data object CloseProject : OpenProjectOption {
        override fun openProject(launcher: PonticelloLauncher) {
            launcher.closeProject()
        }
    }
}
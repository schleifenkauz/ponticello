package ponticello.model.git

import org.eclipse.jgit.lib.ProgressMonitor

interface GitUserInteraction: ProgressMonitor {
    fun authenticate(userCode: String, verificationUri: String)

    fun launch(block: suspend () -> Unit)
}
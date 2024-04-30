package xenakis.ui

import xenakis.model.XenakisProject

interface XenakisListener {
    fun superColliderListening()
    fun displayProject(project: XenakisProject)
    fun displayStartupScreen()
}
package xenakis.ui.launcher

interface ProgressIndicator {
    fun initialStatus(status: String)
    fun displayProgress(progress: Double, status: String)
    fun increaseProgress(delta: Double, status: String)
}
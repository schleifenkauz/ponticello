package xenakis.ui.launcher

interface ProgressIndicator {
    val progress: Double
    fun initialStatus(status: String)
    fun displayProgress(progress: Double, status: String)
    fun increaseProgress(delta: Double, status: String)
}
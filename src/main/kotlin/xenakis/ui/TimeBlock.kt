package xenakis.ui

interface TimeBlock {
    fun getDuration(width: Double): Double

    fun getTime(x: Double): Double

    fun getWidth(duration: Double): Double

    fun getX(time: Double): Double
}
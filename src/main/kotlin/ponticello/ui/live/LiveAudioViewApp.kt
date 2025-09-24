package ponticello.ui.live

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class LiveAudioViewApp : Application() {
    private lateinit var capture: LiveAudioCapture

    override fun start(primaryStage: Stage) {
        val tmpFile = File("tmp.bin")
        val sampleRate = 44100.toDouble()
        val buffer = LiveAudioFileBuffer(tmpFile, sampleRate)
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val mixerInfos = AudioSystem.getMixerInfo().filter { info ->
            val mixer = AudioSystem.getMixer(info)
            mixer != null && mixer.isLineSupported(DataLine.Info(TargetDataLine::class.java, format))
        }
        for ((i, info) in mixerInfos.withIndex()) {
            println("${i + 1}) ${info.name}, ${info.description}")
        }
        val idx = readln().toIntOrNull() ?: 1
        val info = mixerInfos.getOrNull(idx - 1)
        if (info == null) {
            System.err.println("Invalid microphone index $idx")
            return
        }
        val mixer = AudioSystem.getMixer(info)
        val bufferSize = 1024

        val peaks = WaveformPeaks(buffer, minZoom = 4, maxZoom = 12)
        val canvas = WaveformCanvas(peaks, initialDisplayRange = 0.0..10.0)
        canvas.width = 1000.0
        canvas.height = 200.0
        canvas.repaint()

        capture = LiveAudioCapture(buffer, format, mixer, bufferSize)
        capture.start()

        val controls = HBox()
        primaryStage.scene = Scene(VBox(controls, canvas))
        primaryStage.sizeToScene()
        primaryStage.show()
    }

    override fun stop() {
        capture.stop()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(LiveAudioViewApp::class.java, *args)
        }
    }
}
package ponticello.ui.record

import javafx.application.Application
import javafx.application.Application.launch
import javafx.scene.Scene
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import ponticello.impl.rangeTo
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.record.HeapAudioBuffer
import ponticello.model.record.LiveAudioCapture
import ponticello.model.record.WaveformPeaks
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class LiveAudioViewApp : Application() {
    private lateinit var capture: LiveAudioCapture

    override fun start(primaryStage: Stage) {
        val sampleRate = 44100.toDouble()
        val bufferSize = 1024
//        val tmpFile = File("tmp.bin")
//        val buffer = AudioFileBuffer(tmpFile, sampleRate, bufferSize)
        val buffer = HeapAudioBuffer(sampleRate, bufferSize, initialCapacity = (sampleRate * 20).toInt())
        val format = AudioFormat(
            sampleRate.toFloat(), /*sampleSizeInBits*/16, /*channels*/1,
            /*signed*/ true, /*bigEndian*/false
        )
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

        val initialDisplayRange = zero..10.toDecimal()
        val peaks = WaveformPeaks(buffer, minZoom = 4, maxZoom = 12)
        val canvas = LiveWaveformView(peaks, initialDisplayRange, LiveBufferViewConfig.Waveform.default())
//        val canvas = LiveSpectrogramView(buffer, framesPerImage = 100, initialDisplayRange)
//        canvas.start()
        canvas.setPrefSize(1000.0, 500.0)

        capture = LiveAudioCapture(format, mixer, bufferSize)
        capture.start(buffer)

        val controls = HBox()
        primaryStage.scene = Scene(VBox(controls, canvas))
        primaryStage.sizeToScene()
        primaryStage.show()
    }

    override fun stop() {
        capture.stop()
    }
}

fun main(args: Array<String>) {
    launch(LiveAudioViewApp::class.java, *args)
}

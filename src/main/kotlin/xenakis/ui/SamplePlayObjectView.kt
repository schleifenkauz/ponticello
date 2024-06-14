package xenakis.ui

import bundles.createBundle
import javafx.geometry.Rectangle2D
import javafx.scene.Cursor
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import reaktive.Observer
import xenakis.model.SamplePlayObject
import xenakis.sc.view.ObjectSelectorControl

class SamplePlayObjectView(val obj: SamplePlayObject) : ScoreObjectView(obj) {
    private val outBusSelector = ObjectSelectorControl(obj.outSelector, createBundle())
    private lateinit var image: Image
    private val views = mutableListOf<ImageView>()

    private lateinit var contentsObserver: Observer

    override val supportedActions: List<Icon>
        get() = super.supportedActions - Icon.ExtraWindow

    private fun loadSpectrogram() {
        val imageFile = obj.sample.get().spectrogramFile
        if (!imageFile.isFile) return
        image = Image(imageFile.inputStream())
        display()
    }

    private fun display() {
        envelopesPane.children.removeAll(views)
        views.clear()
        val bufDur = (obj.sample.get().duration - obj.startPos) / obj.rate
        var remainingDuration = obj.duration
        while (remainingDuration != 0.0) {
            val imageDur = minOf(remainingDuration, bufDur)
            val view = ImageView(image)
            displaySpectrogram(view, imageDur)
            view.viewOrder = 100.0
            view.layoutX = pane.getWidth(obj.duration - remainingDuration)
            remainingDuration -= imageDur
            views.add(view)
        }
        envelopesPane.children.addAll(views)
    }

    private fun displaySpectrogram(view: ImageView, duration: Double) {
        val pixelsPerSecond = image.width / obj.sample.get().duration * obj.rate
        val minX = pixelsPerSecond * obj.startPos
        val minY = 0.0
        val width = pixelsPerSecond * duration
        val height = image.height
        view.viewport = Rectangle2D(minX, minY, width, height)
        view.fitHeight = prefHeight
        view.fitWidth = pane.getWidth(duration)
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        var newDuration = pane.getDuration(width)
        if (ev.isShiftDown) {
            obj.rate *= obj.duration / newDuration
        } else {
            newDuration = pane.getDuration(width)
            if (cursor in setOf(Cursor.W_RESIZE, Cursor.SW_RESIZE, Cursor.NW_RESIZE)) {
                newDuration = newDuration.coerceAtMost(obj.duration + obj.startPos)
                val deltaStart = obj.duration - newDuration
                obj.startPos = (obj.startPos + deltaStart * obj.rate)
            }
        }
        super.resizeObject(pane.getWidth(newDuration), height, ev, cursor)
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        loadSpectrogram()
        contentsObserver = obj.sample.get().contentsChanged.observe { _, _ -> loadSpectrogram() }
        header.children.add(1, outBusSelector)
    }

    override fun rescale() {
        super.rescale()
        display()
    }
}

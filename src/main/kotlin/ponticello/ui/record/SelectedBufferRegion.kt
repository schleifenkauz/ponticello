package ponticello.ui.record

import fxutils.prompt.nextToTarget
import fxutils.registerShortcuts
import ponticello.impl.DecimalRange
import ponticello.impl.rangeTo
import ponticello.impl.zero

class SelectedBufferRegion(
    parent: LiveAudioBufferView, range: DecimalRange = zero..zero
) : BufferRegion(parent, range) {
    init {
        styleClass.add("selected-buffer-region")
        registerShortcuts {
            on("Shift?+Enter") { ev ->
                parent.createSoundProcess(bufferRange, ev.nextToTarget())
            }
        }
        setOnMouseClicked { ev ->
            if (ev.clickCount >= 2) {
                parent.createSoundProcess(bufferRange, ev.nextToTarget())
                ev.consume()
            }
        }
    }
}
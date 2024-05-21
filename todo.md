## Functionality

### layout groups, horizontal, vertical and both

- highlight groups when selecting objects
- ability to remove objects from their group(s)

### external vst plugins

### global controls

- definitions of global parameters in side window (or even in initialization code, but how to extract then?)
- Global control window with knobs/sliders that can be moved to a touch screen

### sound file objects

- maybe have a configurable playbuf-synth (additional parameters, filters, etc.)
    - defaults to simple PlayBuf
- ability to cut into two separate objects
- simple effects (e.g. reverse)
- spectral view

### how to react to changes of synth definitions?

### completion

- buffer refs
- bus refs
- variables
- global variables
- classes
- SynthDefs
- common parameter definitions

### Compound objects

- creates a sub-canvas in which score objects can be created
- main use is in combination with repeater objects
- only synth-objects and sound-file-objects can be children? (also task objects?)
    - also compound-objects???

### Repeater objects

- dock to a regular ScoreObject
- configurable repetition period
- highlights the times when the object is repeated => sub-grid

### Integrate Buffers and SynthDefs views into startup script?

### Copying and pasting score objects

- ability to copy and paste whole time ranges

### Logging

## Bug fixes

- why can't we have shortcuts in the ScoreView?

## Nice to have

- maybe get rid of object header and move it to toolbar?
    - where to grab memo objects?
- integrate close, maximize, minimize into top toolbar and remove window decoration
- move play-head on (double-)click
- have second semi-transparent vertical line follow the cursor position
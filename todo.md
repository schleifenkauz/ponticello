## Functionality

### sound file objects

- reverse!

### how to react to changes of synth definitions?

- highlight obsolete arguments in control assignment view
- ability to add extra parameters for usage in LFO?

### Make playback more reactive

- can we generate code on the fly, i.e. only shortly before the cursor hits an object?
- global variables are already reactive
- would be interesting to have live-control over constant parameters of individual objects

### Mixing interface in AudioFlowGraph

- special "mixing-node" which has a volume (and pan?) control for every of its inputs

### completion

- buffer refs
- bus refs
- variables
- global variables
- classes
- SynthDefs
- common parameter definitions

### Copying and pasting score objects

- ability to copy and paste time ranges

### Logging

- more logging
- also user options for logging level

### Stretching time regions

- select time, then drag with shift
- stretch objects or only reposition?

### Moving (resizing?) objects with arrow keys

- react to time snap options
  - when no snap, move in screen pixels (?)
- when alt is pressed resize instead
  - shift activates stretching mode

### Minor

- lag values for parameters (adjust with spinner in control assignment)
- allow for more cases of pasting code
- save window positions
- unify knob control and constant control
  - add increment and decrement buttons to slider
  - always display current value (where?)
  - show input dialog on double click
- custom min/max value for envelope controls? (if the spec provided by the SynthDef is to large)
- inter-project SynthDef collection
- effects that work with intermediate buses have to be executed after the flow synths
- fix time display and snapping in envelope editors
- switch to next property with TAB
- avoid hanging when speaking to SuperCollider (timeouts, async, etc.)
- kill sclang.exe and scsynth.exe before startup (?)
- add new envelope points on the path, not at the cursor position
- do we need an extra window for LFO code? 
- make the samples/buses/instruments display more compact and searchable (especially useful for samples)

### Support for patterns

- as synth arguments (must be delta-patterns)
- as standalone objects (`pbind`)
- attaching to a synth in the score (`pmono`)

### Bouncing

- to file or to buffer (that is record to file and then load buffer into SC)
- realtime (easy) and non realtime (not easy, use Score API from SuperCollider)
- or have a live buffer to which everything is recorded, from which you can choose snippets
- can be done via Audacity for now (record from pipewire and then export as WAV to create a buffer in xenakis)

## Long term architectural ideas

- track references to objects, if an object has references, it cannot be removed (necessary?)
- use SuperCollider `Score` object for playback (this also means ability to do NRT bouncing!)

## Bugs

- on startup an unnecessarily large empty space is displayed
  - on scrolling the display works correctly
- sometimes playback stops at a specific score object: find out what causes this and fix it
- sometimes playback is duplicated with a short delay
- why doesn't windows let SuperCollider speak to Xenakis?

## Nice to have

- integrate close, maximize, minimize into top toolbar and remove window decoration
## Functionality

### Make playback more reactive

- can we generate code on the fly, i.e. only shortly before the cursor hits an object?
- global variables are already reactive
- would be interesting to have live-control over constant parameters of individual objects

### completion

- buffer refs
- bus refs
- variables
- global variables
- classes
- SynthDefs
- common parameter definitions

### Copying and pasting score objects

- ability to copy and paste whole time ranges

### Mixing interface in AudioFlowGraph

- special "mixing-node" which has a volume (and pan?) control for every of its inputs

### Logging and error management

- more logging
- also user options for logging level

### Stretching time regions

- select time, then drag with shift
- stretch objects or only reposition?

### Inter-project SynthDef collection

- two ways to add SynthDefs to projects
  - add it from global collection
  - create a new one, which can be added to the global collection if needed

### Improve layout

- fix time display and snapping in envelope editors
- save main window size/position and arrangement of utility panes
- do we need an extra window for LFO code?
- integrate the buses pane into the audio flow window (it is only needed there)
- make the sample pane a searchable popup (but what about adding samples from the file explorer?)
- integrate close, maximize, minimize into top toolbar and remove window decoration

### Unify knob control and constant control

- add increment and decrement buttons to slider
- always display current value (where?)
- show double input dialog on double click (no pun intended)

### Make y position of ScoreObjects relative to screen resolution

- this would simplify working on the same project from different screens
- y coordinate between 0 and 1, automatically scaled to size of score on the screen

### Support for patterns

- as synth arguments (must be delta-patterns)
- as standalone objects (`pbind`)
- attaching to a synth in the score (`pmono`)

### Moving (resizing?) objects with arrow keys

- react to time snap options
- when no snap, move in screen pixels (?)
- when alt is pressed resize instead
- shift activates stretching mode

### More flexible controls for SynthObjects

- untie the controls from the SynthDef parameters
- when creating a SynthObject the object has no controls
- controls can be added either as Envelopes with Alt+Click on the ObjectView or as other controls from the DetailPane
- when adding envelope controls, min/max values can be specified
- controls can also be removed => default values are used in that case
- also controls not present in the SynthDef can be added, which can then be used from LFO code

### Bouncing

- to file or to buffer (that is record to file and then load buffer into SC)
- realtime (easy) and non realtime (not easy, use Score API from SuperCollider)
- or have a live buffer to which everything is recorded, from which you can choose snippets
- can be done via Audacity for now (record from pipewire and then export as WAV to create a buffer in xenakis)

### Minor

- lag values for parameters (adjust with spinner in control assignment)
- allow for more cases of pasting code
- custom min/max value for envelope controls? (if the spec provided by the SynthDef is to large)
- avoid hanging when speaking to SuperCollider (timeouts, async, etc.)
- kill sclang.exe and scsynth.exe before startup (?)
- possibility to replace instrument of a SynthObject, reusing common controls

## Long term architectural ideas

### track references to objects, if an object has references, it cannot be removed (necessary?)

### use SuperCollider `Score` object for playback (this also means ability to do NRT bouncing!)

## Bugs

- effects that work with intermediate buses have to be executed after the flow synths
- sometimes the first ScoreObject is not played, when the cursor starts exactly over it
- fix time display and snapping in envelope editors
- on startup an unnecessarily large empty space is displayed
- on scrolling the display works correctly
- sometimes playback stops at a specific score object: find out what causes this and fix it
- sometimes playback is duplicated with a short delay
- why doesn't windows let SuperCollider speak to Xenakis?

## Functionality

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

### Improve layout

- save main window size/position and arrangement of utility panes
- integrate close, maximize, minimize into top toolbar and remove window decoration

### Support for patterns

- as synth arguments (must be delta-patterns)
- as standalone objects (`pbind`)
- attaching to a synth in the score (`pmono`)

### More flexible controls for SynthObjects

- when adding envelope controls, the spec could be edited
- also: options to sync from/to SynthDef spec

### Bouncing

- to file or to buffer (that is record to file and then load buffer into SC)
- realtime (easy) and non realtime (not easy, use Score API from SuperCollider)
- or have a live buffer to which everything is recorded, from which you can choose snippets
- can be done via Audacity for now (record from pipewire and then export as WAV to create a buffer in xenakis)

### Minor

- allow for more cases of pasting code
- kill sclang.exe and scsynth.exe before startup (?)

## Long term architectural ideas/question

- track references to objects, if an object has references, it cannot be removed (necessary?)
- use SuperCollider `Score` object for playback (this also means ability to do NRT bouncing!)
- should SynthDefs and VSTPlugin have a separate registry
- Do we need groups?

## Bugs

- effects that work with intermediate buses have to be executed after the flow synths
- fix time display and snapping in envelope editors
- weird window behaviour on startup
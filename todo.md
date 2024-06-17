## Functionality

### sound file objects

- reverse!

### how to react to changes of synth definitions?

- highlight obsolete arguments in control assignment view

### Make playback more reactive

### Mixing interface in AudioFlowGraph

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

### Logging

### Stretching time regions

- select time, then drag with shift
- stretch objects or only reposition?

### Minor

- lag values for parameters (adjust with spinner in control assignment)
- allow for more cases of pasting code
- save window positions
- unify knob control and constant control
  - add increment and decrement buttons to slider
  - always display current value (where?)
  - show double input dialog on double click (no pun intended)
- show double input dialog when double-clicking on envelope handle
- custom min/max value for envelope controls? (if the spec provided by the SynthDef is to large)

### Support for patterns

- as synth arguments (must be delta-patterns)
- as standalone objects (`pbind`)
- attaching to a synth in the score (`pmono`)

### Bouncing

- to file or to buffer (that is record to file and then load buffer into SC)
- realtime (easy) and non realtime (not easy, use Score API from SuperCollider)
- or have a live buffer to which everything is recorded, from which you can choose snippets
- can be done via Audacity for now (record from pipewire and then export as WAV to create a buffer in xenakis)

## Architecture

- track references to objects, if an object has references, it cannot be removed (necessary?)
- avoid hanging when speaking to SuperCollider (timeouts, async, etc.)
- use SuperCollider `Score` object for playback (this also means ability to do NRT bouncing!)

## Bugs

- when pasting multiple objects at once sometimes some of the clone/copies are not shown
  - they are present in the score
  - they are present in the views-map of the ScorePane
  - they are present in the children of the ScorePane
  - they are not shown when reopening the score
  - what could it be???

## Nice to have

- integrate close, maximize, minimize into top toolbar and remove window decoration
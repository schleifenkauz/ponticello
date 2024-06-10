## Functionality

### external vst plugins

- usage in ad hoc synths

### sound file objects

- maybe have a configurable `playbuf`-synth (additional parameters, filters, etc.)
  - defaults to simple `PlayBuf`
- simple effects (e.g. reverse)
- spectral view

### how to react to changes of synth definitions?

- highlight obsolete arguments in control assignment view

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
- fixed duration synths (don't need to be released)
  - maybe also distinguish between gated SynthDefs (can be `release`d) and those that have to be `free`d
- command to introduce parameter for constant expression in SynthDef
- save the snap and grid visibility settings (per project?)
- update SynthDefs on closing configuration window
- add SynthDefs on loading project
- allow for more cases of pasting code
- add to expr list with comma
- looping period specifiable in beats/ticks/bars
- ability to drag multiple objects at once

### Support for patterns

- as synth arguments (must be delta-patterns)
- as standalone objects (`pbind`)
- attaching to a synth in the score (`pmono`)

### Bouncing

- to file or to buffer (that is record to file and then load buffer into SC)
- realtime (easy) and non realtime (not easy, use Score API from SuperCollider)
- or have a live buffer to which everything is recorded, from which you can choose snippets

## Architecture

- track references to objects, if an object has references, it cannot be removed (necessary?)
- avoid hanging when speaking to SuperCollider (timeouts, async, etc.)
- we have to have access to the registry while deserializing the score objects!
  - write custom serializer for `XenakisProject`
- use SuperCollider `Score` object for playback (this also means ability to do NRT bouncing!)

## Bug fixes

- why can't we have shortcuts in the ScoreView?
- the mouse position tracker line sometimes interferes with clicks (disabled for now)
- why are HextantTextFields sometimes too small when reopening editors?

## Nice to have

- integrate close, maximize, minimize into top toolbar and remove window decoration
- auto resize prompt windows (e.g. parameter definition prompts)
- maybe show control assignment view in tool-pane?
- ad hoc synths
- display all non-envelope controls of a synth in its header?
  - easy access but might end up cluttering the interface
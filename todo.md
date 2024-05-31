## Functionality

### external vst plugins

- piano roll tool

### sound file objects

- maybe have a configurable playbuf-synth (additional parameters, filters, etc.)
    - defaults to simple PlayBuf
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

### Better grid functionality

- time grid in seconds (display on/off)
- beat grid (display on/off)
  - specify tempo (beats/second), beats per bar (on/off), and beat subdivisions (on/off)
- can snap to bars/beats/subdivisions/custom amount in ms

### Copying and pasting score objects

- ability to copy and paste whole time ranges

### Rethink the buffer/sound-file system

### Logging

### Minor

- lag values for parameters (adjust with spinner in control assignment)

### Support for patterns

- as synth arguments (must be delta-patterns)
- as standalone objects (pbind)
- attaching to a synth in the score (pmono)

### Bouncing

- to file or to buffer (that is record to file and then load buffer into SC)
- realtime (easy) and non realtime (not easy, use Score API from SuperCollider)

## Architecture

- track references to objects, if an object has references, it cannot be removed

## Bug fixes

- why can't we have shortcuts in the ScoreView?
- the mouse position tracker line sometimes interferes with clicks (disabled for now)
- why are HextantTextFields always too small when reopening editors?

## Nice to have

- integrate close, maximize, minimize into top toolbar and remove window decoration
- auto resize prompt windows (e.g. parameter definition prompts)
- maybe show control assignment view in tool-pane?
- ad hoc synths
- display all non-envelope controls of a synth in its header?
  - easy access but might end up cluttering the interface
## Functionality

### layout groups, horizontal, vertical and both

- highlight groups when selecting objects
- ability to remove objects from their group(s)

### external vst plugins

- piano roll tool

### sound file objects

- maybe have a configurable playbuf-synth (additional parameters, filters, etc.)
    - defaults to simple PlayBuf
- simple effects (e.g. reverse)
- spectral view

### how to react to changes of synth definitions?

- parameter renaming!

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

### Naming in Score and in SuperCollider

- separate name for Synth of each clone (or even hash-code suffix for every created synthesizer)
- what about renaming buses and buffers?
- naming checks to avoid duplicates

### Rethink the buffer/sound-file system

### Logging

### Minor

- lag values for bus value controls (adjust with spinner in control assignment)
- use SynthDefs imported from SuperCollider code
  - set apart imported and created SynthDefs in UI

### Support for patterns

- as synth arguments (must be delta-patterns)
- as standalone objects (pbind)
- attaching to a synth in the score (pmono)

## Bug fixes

- why can't we have shortcuts in the ScoreView?
- make the interaction with sclang less hacky (maybe use Java OSC library?)
- the mouse position tracker line sometimes interferes with clicks (disabled for now)
- do not throw Exception when undo is not possible on Ctrl+Z

## Nice to have

- integrate close, maximize, minimize into top toolbar and remove window decoration
- auto resize prompt windows (e.g. parameter definition prompts)
- maybe show object toolbar on context click instead of top bar
- ad hoc synths
- display all non-envelope controls of a synth in its header?
  - easy access but might end up cluttering the interface
## Functionality

### layout groups, horizontal, vertical and both

- highlight groups when selecting objects
- ability to remove objects from their group(s)

### external vst plugins

### sound file objects

- maybe have a configurable playbuf-synth (additional parameters, filters, etc.)
    - defaults to simple PlayBuf
- simple effects (e.g. reverse)
- spectral view

### how to react to changes of synth definitions?

### Piano roll tool

### Mixing interface in AudioFlowGraph

### Two resize modes for objects with associated envelopes

- when shift is pressed, resize by scaling the envelopes (which is easier given the current implementation)
- when shift is not pressed, resize by removing/adding segments to the envelopes

### completion

- buffer refs
- bus refs
- variables
- global variables
- classes
- SynthDefs
- common parameter definitions

### Integrate Buffers and SynthDefs views into startup script?

### Copying and pasting score objects

- ability to copy and paste whole time ranges

### Naming in Score and in SuperCollider

- separate name for synthesizer of each clone (or even hash-code suffix for every created synthesizer)
- what about renaming buses and buffers?
- naming checks to avoid duplicates

### Logging

## Bug fixes

- why can't we have shortcuts in the ScoreView?

## Nice to have

- integrate close, maximize, minimize into top toolbar and remove window decoration
- auto resize prompt windows (e.g. parameter definition prompts)
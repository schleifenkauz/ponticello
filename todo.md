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
- main use is in combination with loops
- only synth-objects and sound-file-objects can be children? (also task objects?)
    - also compound-objects???

### Loops

- put original and clones in their own layout group
- connect objects with arrows?
  - removing arrows means disconnecting the references, so that incremental changes to the loop can be made

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
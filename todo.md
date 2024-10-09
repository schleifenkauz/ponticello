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

- also report exceptions happening in SuperCollider

### Stretching time regions

- select time, then drag with shift
- stretch objects or only reposition?

### UI niceties

- save main window size/position and arrangement of utility panes
- integrate close, maximize, minimize into top toolbar and remove window decoration
- better positioning of flow graph arrows

### Support for patterns

- as synth arguments (must be delta-patterns)
- as standalone objects (`pbind`)
- attaching to a synth in the score (`pmono`)

### Minor

- allow for more cases of pasting code
- kill sclang.exe and scsynth.exe before startup (?)
- specify expected channels (and ar/kr) for bus and buffer parameters
- lhs of assignments can be compound expressions
- clear ServerTree and ServerBoot when closing a project
  - or just reboot sclang.exe, this would mean generally restructuring the startup process
- re-enable allocated buffers (can be used with WrBuf/RdBuf SynthDefs)

## Long term architectural ideas/questions

- track references to objects, if an object has references, it cannot be removed (necessary?)
- use SuperCollider `Score` object for playback (this also means ability to do NRT bouncing!)
- should `SynthDef`s and `VSTPlugin` have a separate registry
- idea: edit a clone of a `ScoreObjectGroup` in a `SubWindow` and sync it with the main score on demand
  - well-suited for DJ use cases
- time jumps
  - like goto statements
  - can have conditions (examples...?)
- vertical sections (like JavaFX `SplitPane`)
  - draggable dividers
  - dragging them stretches/shrinks all the sections uniformly
  - double click with shift on a divider adds a new section
  - sections can have associated buses, that are used for any new Synths that have bus parameters
- implicitly duplicate SynthDefs for mono/stereo
- `Task`s and `Pbind`s as instruments
  - introduce `ProcessObject` which is a bit like `SynthObject` but takes a `ProcessDef`
- VSTPlugin `SynthObject`s
  - retrieve parameters from plugin info, make them controllable in the `DetailPane` or as envelopes
- Rethink the Json serialization of Hextant editors
- Attach transformations to buses not to flows
  - order of transformation synths could be decided by the order of the buses in the registry
  - how to display/manage flows?
  - graphical tools for EQ, compression (multi-band), reverb, etc. (integrate as special editors?)
- attach a default bus object to SubScores

## Bugs

- weird window behaviour on startup
- something is not quite right yet in `ScoreEventCollector`

### Namen überlegen

- vielleicht: Ponticello (wegen Brücke)
- oder: multiphonix
- oder: Antiphonoskop
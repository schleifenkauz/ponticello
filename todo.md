### Questions

- is the `AdhocSynth` still needed?
- does each `ConfigurableParameterizedObjectDef` need its own context
    - or does it take the one of its registry?
- separate registries for audio and control buses?

### Needs Fix

- resizing behaviour is not quite right
- something is not quite right yet in `ScoreEventCollector` (especially when resizing objects...)
- moving objects to sub score parent is buggy
- sub-objects get buggy after unlinking group objects
    - the new objects don't get added to the `ScoreEventCollector`
- deleting objects doesn't remove them from the events collector...

### Minor

- is the envelope magnifier really needed?
- ability to specify added time in beats/bars/ticks
- ability to copy and paste whole time ranges
- what to do when user selects a new `SynthDef` for a synth object
    - re-sync parameter controls in some way...
- unify window positioning
- asr parameter type?
  - with curve parameter

## New Functionality

### completion

- variables
- global variables
- classes

- also highlight unresolved variables!

### Stretching time regions

- select time, then drag with shift
- stretch objects or only reposition?

### UI niceties

- option to put documentation browser into fixed window
- make the quit button nicer
- don't squish the item cell list in detail pane mode

### Support for patterns

- as synth arguments
    - either delta patterns -> `Pmono` for all the delta pattern arguments
    - or global pattern streams -> simply get `<stream>.next` (implemented)
- as standalone objects (`pbind`)
    - this can be done with tasks and a special expression editor for `PLbindef`s

### More efficient communication with SC

- add score objects as functions in sc
    - what about object groups
- in which cases can we talk to scsynth directly?
    - bus value update (could really improve live modulation performance)
  - SynthObjects could be created by sending messages to scsynth directly

### Own notification API integrating with the LogPane

### Maybe use ControlsFX RangeSlider

- https://controlsfx.github.io/features/rangeslider/

### Track references to objects

- buses, samples, score objects, etc.
- if an object has references, it cannot be removed
- is this necessary?

### use SuperCollider `Score` object for playback

- this means ability to do NRT bouncing!

### Rethink VSTPlugins

- should `SynthDef`s and `VSTPlugin` have a separate registry
- retrieve parameters from plugin info, make them controllable in the `DetailPane` or as envelopes

### Live mode

- time jumps
    - like goto statements
    - can have conditions (examples...?)

### DJ functionality

- split stems with demucs
- do we need a global tempo?
- a track is a sample with a tempo grid
- when adding a track to the score, choose how to align it with the existing grids

### Different curvatures for envelopes

### Is vertical scroll/zoom needed?

### Drum sequencer objects

## Was unterscheidet Ponticello von anderen DAWs?

- Möglichkeit beliebig komplexe SynthDefs zu bauen.
- Unterstützung des Pattern-Systems von SuperCollider (bald)
- Möglichkeit Programmschnipsel in die Partitur einzubinden.
- geschachteltes Objektsystem

### Namen überlegen

- vielleicht: Ponticello (wegen Brücke)
- oder: multiphonix
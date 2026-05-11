### Questions

### Needs Fix

### Minor

- is the envelope magnifier really needed?
- ability to specify added time in beats/bars/ticks
- ability to copy and paste whole time ranges
- what to do when user selects a new `SynthDef` for a synth object
    - re-sync parameter controls in some way...

## New Functionality

### Stretching time regions

- select time, then drag with shift
- stretch objects or only reposition?

### UI niceties

- special layout for lambdas as function arguments

### Extend Support for patterns

- as synth arguments
    - either delta patterns -> `Pmono` for all the delta pattern arguments
    - or global pattern streams -> simply get `<stream>.next` (implemented)
- as standalone objects (`pbind`)
    - this can be done with tasks and a special expression editor for `PLbindef`s

### More efficient communication with SC

- in which cases can we talk to scsynth directly?
    - bus value update (could really improve live modulation performance)

### Own notification API integrating with the LogPane

### Track references to objects

- buses, samples, score objects, etc.
- if an object has references, it cannot be removed
- is this necessary?

### use SuperCollider `Score` object for playback

- this means ability to do NRT bouncing!

### DJ functionality

- split stems with demucs
- do we need a global tempo?

### Different curvatures for envelopes

### Is vertical scroll/zoom needed?
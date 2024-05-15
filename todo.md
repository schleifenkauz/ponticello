## Functionality

### layout groups, horizontal, vertical and both

- highlight groups when selecting objects 
- ability to remove objects from their group(s)

### external vst plugins

- global collection of VSTPluginControllers
- they can be used in individual SynthDefs and ad hoc synths
- can also be defined on the fly from within SynthDefs

### global controls

- definitions of global parameters in side window (or even in initialization code, but how to extract then?)
- Global control window with knobs/sliders that can be moved to a touch screen

### sound buffer objects

- resize => adjust startPos/cutoff
- resize with shift => stretch/squish
- maybe have a configurable playbuf-synth (additional parameters, filters, etc.)
  - defaults to simple PlayBuf
- ability to cut into two separate objects

### select time intervals

- ability to delete them
- ability to add time intervals

### how to react to changes of synth definitions?

### completion

- buffer refs
- bus refs
- variables
- global variables
- classes
- SynthDefs
- common parameter definitions

### Compound pattern objects

- creates a sub-canvas in which score objects can be created
- repetition period is defined by the width of the sub-canvas

## Bug fixes

## Nice to have

- integrate close, maximize, minimize into top toolbar and remove window decoration
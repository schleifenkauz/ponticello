## Functionality

### completion

- variables
- global variables
- classes

### Stretching time regions

- select time, then drag with shift
- stretch objects or only reposition?

### UI niceties

- option to put documentation browser into fixed window
- move sync-action to item scroll list (synth defs and process defs)
  - but display when shown in sub window?
- move add parameter button to bottom of parameter list
- make the quit button nicer

### Better help popups for SuperCollider API

- press Ctrl+P to show parameter info of current selected cell

### Support for patterns

- as synth arguments 
  - either delta patterns -> `Pmono` for all the delta pattern arguments
  - or global pattern streams -> simply get `<stream>.next` (implemented)
- as standalone objects (`pbind`)
  - this can be done with tasks and a special expression editor for `PLbindef`s 

### Attach transformations to buses not to flows

- how to mute
  - disable flow graph arrows
  - or mute individual buses
- graphical tools for EQ, compression (multi-band), reverb, etc.
  - integrate as special editors?
- or open transformation chain by clicking on bus in the flow graph
- how to notify user about loops in the flow graph

### More efficient communication with SC

- add score objects as functions in sc 
  - what about object groups
- in which cases can we talk to scsynth directly?
  - bus value update (could really improve live modulation performance)

### Code windows

1. Playground
2. Setup: executed before booting server
3. ServerBoot: executed after server has booted
4. ServerTree: executed after the tree was cleared

- Shortcut/Button for executing the selected code (`Ctrl+Enter`)
  - either one editor or consecutive statements in a block editor
- Result/Error popup shown with (visually indicated) option to copy
- Command variant that also removes the executed code (`Ctrl+Shift+Enter`)
  - useful for quick queries
- Make it easy to copy/cut/paste between the 4 code panes
- also have a tool window for console output
  - maybe implement SuperCollider logging level

### Questions

- is the `AdhocSynth` still needed?

### Needs Fix

- improve the timeline
  - maybe only move playback cursor on timeline?
  - display timeline not as part of the score (below or on top?)
- resize copied object image when zooming
- reimplement startup progress bar
- why aren't the duplicate buttons shown?
- weird duplicating bug when pasting

### Minor

- ability to specify added time in beats/bars/ticks
- option to create external automation (adding a kr-write `SynthObject`) for numerical parameter
- select new objects after unlinking
- make pasting with shortcut (V and Shift+V)
  - how to query mouse position (`javafx.scene.robot.Robot.mousePosition`)
- object insertion with INSERT
  - how to lookup which score pane is hovered?
- live updates for envelopes
- ability to copy and paste whole time ranges
- own editor type for control references
  - can only be used in LFO argument expressions
- what to do when user selects a new `SynthDef` for a synth object
  - re-sync parameter controls in some way...
- highlight unresolved references of object selectors
- unify window positioning
- more adequate midi contexts 
  - based on focused window
- add play button to samples
- global variable in SuperCollider for list of all samples (`ALL_SAMPLES`)
- undo managers for sub scenes 
  - undo/redo actions on tool panes
- curve parameter type
- asr parameter type?
- choose alternative addresses if the default ones are busy
- reconsider which threads to use (coroutines?)

## Long term ideas

### LiveSynth space

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

- edit a clone of a `ScoreObjectGroup` in a `SubWindow` and sync it with the main score on demand
- sub score windows for live loops
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

### Ability to show score objects in resizable/movable floating window (with detail pane)

### Are there better methods of navigating through the score?

- for example a little overview pane at the bottom (like in VSCode)
- is arrow key navigation inside the score somehow possible?

### Drum sequencer objects

### Divide the score into multiple tracks

- vertical sections (like JavaFX `SplitPane`)
- draggable dividers
- dragging them stretches/shrinks all the other sections uniformly
- double click with shift on a divider adds a new section
- sections can have associated buses, that are used for any new Synths that have bus parameters

## Bugs

- resizing behaviour is not quite right
- something is not quite right yet in `ScoreEventCollector` (especially when resizing objects...)
- moving objects to sub score parent is buggy
- sub-objects get buggy after unlinking group objects
  - the new objects don't get added to the `ScoreEventCollector`
- recording doesn't work
- cutting doesn't work

## Was unterscheidet Ponticello von anderen DAWs?
- Möglichkeit beliebig komplexe SynthDefs zu bauen.
- Unterstützung des Pattern-Systems von SuperCollider (bald)
- Möglichkeit Programmschnipsel in die Partitur einzubinden.
- geschachteltes Objektsystem

### Namen überlegen

- vielleicht: Ponticello (wegen Brücke)
- oder: multiphonix
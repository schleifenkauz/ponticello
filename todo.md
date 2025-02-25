## Functionality

### completion

- buffer refs
- bus refs
- variables
- global variables
- classes
- SynthDefs
- common parameter definitions

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

### Reorganize startup and project opening

- don't start sclang.exe on startup
- start new sclang instance when opening a project and kill the process when closing the project

### Unclutter the Json serialization of Hextant editors

### Attach transformations to buses not to flows

- flow graph only for managing the order of execution
  - ability to reroute arrows in flow graph
- how to mute?
  - disable flow graph arrows
  - or mute individual buses
- graphical tools for EQ, compression (multi-band), reverb, etc.
  - integrate as special editors?
- track view, where the different buses are listed horizontally and associated transformations are ordered vertically
- or open transformation chain by clicking on bus in the flow graph

### Action system to have a better shortcut architecture

- recognize transport shortcuts from all windows

### Improve zooming functionality

- zoom tool: (selectable with `Z`)
- zoom with `Ctrl+MINUS` and `Ctrl+PLUS`

### Minor

- specify expected channels (and ar/kr) for bus and buffer parameters
- lhs of assignments can be compound expressions
- re-enable allocated buffers (can be used with WrBuf/RdBuf SynthDefs)
- ability to specify numerical parameter as envelope only/constant only
- new parameter type: buffer position (range depends on supplied buffer)
  - or use 0..1 range and scale to buffer duration
- reordering parameters and controls
- ability to rename controls?
- ability to specify value for horizontal envelope lines via input prompt (how? double click displays )
- ability to specify added time in beats/bars/ticks
- make floating windows movable/resizable
- option to create external automation (adding a kr-write `SynthObject`) for numerical parameter
- shortcut for selecting all instances of the selected object (for example `Ctrl+Shift+A`)
- select new objects after unlinking
- specifying default value for control busses
- make pasting with shortcut (V and Shift+V)
  - how to query mouse position (`javafx.scene.robot.Robot.mousePosition`)
- object insertion with INSERT
- live updates for envelopes
- ability to copy and paste whole time ranges
- modularize and cleanup stylesheets
- option to copy samples to project directory
- searching and selecting object details
  - better text search implementation
- improve the timeline

## Long term ideas

### Ability to view all the ScoreObjects

- space for objects to live in, that are not (yet) in the main score

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

### Different curvatures for envelopes

### Is vertical zoom needed?

### Ability to execute commands using a query/update language

### Ability to show score objects in resizable/movable floating window (with detail pane)

### Are there better methods of navigating through the score?

- for example a little overview pane at the bottom (like in VSCode)
- is arrow key navigation inside the score somehow possible?

### Ability to take snapshots of the main score (basic version control)

### Divide the score into multiple tracks

- vertical sections (like JavaFX `SplitPane`)
- draggable dividers
- dragging them stretches/shrinks all the other sections uniformly
- double click with shift on a divider adds a new section
- sections can have associated buses, that are used for any new Synths that have bus parameters

### Nicer console

- use Hextant editors for commands

## Bugs

- resizing behaviour is not quite right
- something is not quite right yet in `ScoreEventCollector` (especially when resizing objects...)
- moving objects to sub score parent is buggy
- wait for synth to be allocated before applying envelopes
- flow graph doesn't react to change of group for flow (only after reopening the project)
- sub-objects get buggy after unlinking group objects
  - the new objects don't get added to the `ScoreEventCollector`
- selection based region display is not quite right
- renaming controls doesn't work
- creating custom parameters doesn't work
- crash when moving the border between ScoreView and DetailPane (probably memory issue)
- recording doesn't work

### Namen überlegen

- vielleicht: Ponticello (wegen Brücke)
- oder: multiphonix
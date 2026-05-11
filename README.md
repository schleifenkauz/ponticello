# Ponticello

*Ponticello* is a tool for composing and performing electronic music.
It is based on the audio programming language [SuperCollider](https://supercollider.github.io/).

SuperCollider's code-based approach offers a uniquely versatile and concise way of working with sound.
It allows complex structures to be described with just a few lines,
and it invites ways of thinking about music that differ completely from traditional notation.

But code as a compositional medium also has its drawbacks and limitations.
Reading through a text file does not give an immediate sense of how musical events relate to one another in time.
A graphical score can offer that immediacy:
When time flows left to right, and simultaneous events share the same horizontal position,
musical relationships become easier to see at a glance.

What is needed is a tool that allows direct interaction with a graphical score,
while still offering the full possibilities of SuperCollider's approach to sound synthesis and musical organization.
Ponticello is an attempt to build such a bridge:
linking the versatility of code as a compositional medium with the intuitiveness of graphical scores,
while drawing on concepts from both notational paradigms.

### Ponticello's score model

Ponticello models the electronic score as a timeline of score objects specifying temporally extended processes.
Each score object references a SynthDef,
which encapsulates the synthesis or signal-processing code executed during playback.
(For those who are maybe more familiar with visual audio programming languages like MAX or Pd:
a SynthDef is basically a coded description of an audio synthesis graph with a set of named parameters,
which expose the controllable aspects of the synthesis.)
In SuperCollider, SynthDefs define a signal-processing graph composed of unit generators (UGens).
Each UGen performs a specific digital signal processing
function, such as oscillation, filtering, or physical modeling, and outputs a stream of values. UGens are connected
by using the output of one as the input for another, or by
reading from and writing to global buses.
SynthDefs define a set of parameters that expose the controllable aspects of the signal processing.
Each score object maintains a set of controls that are assigned to the parameters of its associated SynthDef.
Controls can take several forms:
• constant values,
• control or audio buses,
• automation curves defined over score-time.
• SuperCollider expressions evaluated at runtime.
When an expression evaluates to a UGen, it is used to modulate the corresponding parameter.

#### Synchronization in mixed acoustic-electronic music

In addition to functioning as a composition environment,
Ponticello also explores new ways of coordinating electronic processes with live performers.
Ponticello supports mixed acoustic-electronic music performances
in which electronic playback follows a human conductor.
Instead of synchronizing electronics using fixed digital click tracks,
the system allows the computer to adapt its timing to the conductor's flexible pulse.

![Conductor synchronization diagram](https://schleifenkauz.de/img/skizze_reduziert.png)

During performance, the conductor is captured by a video camera,
and the inferred tempo is used to continuously control the playback speed of the electronic score.
As a result, electronic processes can stretch and compress in time together with the ensemble,
responding naturally to rubato, accelerandi, and ritardandi.
This allows electronic playback to maintain tight coordination with the ensemble
while preserving the performers' freedom to shape musical time expressively.

## Installation

Ponticello uses the SuperCollider platform for audio synthesis.
It can be downloaded [here](https://supercollider.github.io/downloads.html).

To install Ponticello, execute the SuperCollider script [setup_ponticello.scd](setup_ponticello.scd),
which will walk through the setup. 
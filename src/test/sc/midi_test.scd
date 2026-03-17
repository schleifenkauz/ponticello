(
SynthDef(\sine) { arg bus = 0, freq = 440, amp = 0.1, pan = 0;
	var snd = SinOsc.ar(freq) * amp;
	snd = snd * Env.adsr(\attack.kr(0.002), \decay.kr(0.3), \sustain.kr(0.5), \release.kr(0.5), \peak.kr(1)).kr(Done.freeSelf, \gate.kr(1));
	snd = Pan2.ar(snd, pan);
	Out.ar(bus, snd);
}.add;

SynthDef(\vsti) { arg out = 0;
	var snd = VSTPlugin.ar(nil, 2);
	Out.ar(out, snd);
}.add;

~sine = SynthInstrument(\sine);
)


MidiTrack.init
(
t.release;
g.free;
g = Group.new;
t = MidiTrack.new(g, "USB-MIDI", []);
)

(
~vst_synth = Synth(\vsti);
~pianoteq = VSTPluginController(~vst_synth).open("Pianoteq 9.vst3");
~piano = VSTMidiInstrument(~pianoteq)
)

~pianoteq.editor;
~pianoteq.info.parameters
(
~synth = SoundProcessMidiInstrument.create('proc', ~synth, controls: [
	ExprControl('freq') { |inst| inst.get('pitch').midicps },
	ExprControl('amp') { |inst| inst.get('velocity').linlin(20, 100, -60, 0).dbamp },
	ValueControl('decay', 1), ValueControl('sustain', 0.1)
]);
t.instruments = [~proc, ~piano]
)

t.instruments = [~proc]

~src = (server_latency: 0, player_id: 0, chan: 0);
t.noteOn(60, 64, ~src)
t.noteOff(60, 0, ~src)

(
~pos = (y: 0, t: 0)
~note = SoundProcess.create('note', MIDIInstrument.new, [ValueControl("pan", -1)], duration: 5);
~inst = ~note.startNewInstance(~pos, 0, [\pitch -> 1], 0, 0, t)
~note.getInstanceAt(~pos).release
)

~pianoteq.midi.noteOn(1, 60, 60)
~pianoteq.midi.allNotesOff(1)

~instr.allNotesOff

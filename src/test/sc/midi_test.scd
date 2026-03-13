(
SynthDef(\sine) { arg bus = 0, freq = 440, amp = 0.1;
	var snd = SinOsc.ar(freq) * amp;
	snd = snd * Env.adsr(\attack.kr(0.002), \decay.kr(0.3), \sustain.kr(0.5), \release.kr(0.5), \peak.kr(1)).kr(Done.freeSelf, \gate.kr(1));
	Out.ar(bus, snd ! 2);
}.add;

SynthDef(\vsti) { arg out = 0;
	var snd = VSTPlugin.ar(nil, 2);
	Out.ar(out, snd);
}.add;

~sine = SynthInstrument(\sine);

~arpeggiator = RoutineInstrument.new('arp', (delay: 0.3, decay: 5, interval: 4), { |inst|
	var velocity = inst.getControlValue('velocity');
	var delay = inst.getControlValue('delay');
	var decay = inst.getControlValue('decay');
	var interval = inst.getControlValue('interval');
	~last_pitch = inst.pitch;
	while { velocity >= 10 } {
		delay.wait;
		inst.midiTrack.noteOff(velocity, ~last_pitch, src: inst.midiSrc);
		~last_pitch = ~last_pitch + interval;
		velocity = velocity - decay;
		postf("ON: %\n", ~last_pitch);
		inst.midiTrack.noteOn(velocity, ~last_pitch, src: inst.midiSrc);
	};
	inf.wait;
}, onFinished: { |inst|
	if (~last_pitch != inst.pitch) {
		postf("finished %, src: %\n", ~last_pitch, inst.midiSrc);
		inst.midiTrack.noteOff(0, ~last_pitch, src: inst.midiSrc);
	}
});

~ponticello_addr = NetAddr("localhost", 7775);
)

(
t.release;
t = MidiTrack.new("USB-MIDI MIDI 1", [], (addAction: \addToTail, target: s.defaultGroup));
)

(
~vst_synth = Synth(\vsti);
~pianoteq = VSTPluginController(~vst_synth).open("Pianoteq 9.vst3");
)

~pianoteq.editor;

(
~proc = SoundProcess.create('proc', ~sine, duration: nil, controls: [
	ExprControl('freq') { |inst| inst.getControlValue('pitch').midicps },
	ExprControl('amp') { |inst| inst.getControlValue('velocity').linlin(20, 100, -60, 0).dbamp },
	ValueControl('decay', 1), ValueControl('sustain', 0.1)
]);

~arp = SoundProcess.create('arp', ~arpeggiator, duration: nil, controls: [
	ValueControl('delay', 0.5),
	ValueControl('decay', 8),
	ValueControl('interval', 4)
]);

t.instruments = [SoundProcessInstrument(~arp), ~pianoteq.midi, SoundProcessInstrument(~proc)];
)

~pianoteq.midi.noteOn()

~instr.allNotesOff
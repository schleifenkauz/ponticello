(
SynthDef(\sine) { arg bus = 0, freq = 440, amp = 0.1;
	var snd = SinOsc.ar(freq) * amp;
	snd = snd * Env.perc(0.01, \duration.kr(1)).kr(Done.freeSelf);
	FreeSelf.kr(1 - \gate.kr(1));
	Out.ar(bus, snd ! 2);
}.add;
~sine = SynthInstrument(\sine);

~proc = SoundProcess.create('proc', ~sine, controls: [ValueControl('freq', 200)], duration: 0.5);

~beat = RoutineInstrument('beat', ()) { |inst|
	loop {
		inst.startChildInstance(~proc);
		0.5.wait;
	}
};

~beat_proc = SoundProcess.create('beat_proc', ~beat, duration: inf);

~ponticello_addr = NetAddr("localhost", 7775);
)


(
s.waitForBoot {
	~inst = ~proc.createInstance(pos: nil, cutoff: 0, extra_controls: (amp: 0.02))
	.start((addAction: \addToTail, target: s.defaultGroup), s.latency, 0)
}
)

(
s.waitForBoot {
	~inst.restart;
}
)

(
s.waitForBoot {
	~proc.replaceControl(ValueControl('freq', 300));
}
)

(
s.waitForBoot {
	~proc.replaceControl(LFOControl('freq', []) { SinOsc.kr(0.1).exprange(250, 350) });
}
)

(
s.waitForBoot {
	~inst = ~proc.createInstance(pos: nil, cutoff: 0, extra_controls: (amp: 0.005))
	.start((addAction: \addToTail, target: s.defaultGroup), s.latency, 0, run: false)
	3.wait;
	~inst.run(true)
}
)

(
s.waitForBoot {
	~beat_inst = ~beat_proc.createInstance(pos: nil, cutoff: 0)
	.start((addAction: \addToTail, target: s.defaultGroup), s.latency, 0)
}
)

(
SynthDef(\test) {
	var snd = SinOsc.ar * 0.01 ! 2;
	PonticelloPlayback.freeAfter(\duration.kr(5));
	Out.ar(0, snd);
}.add;

SynthDef(\test2) {
	var snd = SinOsc.ar * 0.01 ! 2;
	var env = PonticelloPlayback.asrEnv(\duration.kr(5), attack:1, release:1, curve: \linear);
	Out.ar(0, snd * env);
}.add
)

x = Synth(\test2, [duration: 3])
x.release
x.set(\duration, 2)

(
var rout = Routine {
	10.do {
		1.0.rand.wait;
	};
};
while { rout.next.postln.notNil }
)


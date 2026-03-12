(
SynthDef(\sine) { arg bus = 0, freq = 440, amp = 0.1;
	var snd = SinOsc.ar(freq) * amp;
	FreeSelf.kr(1 - \gate.kr(1));
	Out.ar(bus, snd ! 2);
}.add;
~sine = SynthInstrument(\sine);

~proc = SoundProcess.create('proc', ~sine, duration: nil, controls: [ValueControl('freq', 200)]);

~ponticello_addr = NetAddr("localhost", 7775);
)


(
s.waitForBoot {
	~inst = ~proc.createInstance(pos: nil, cutoff: 0, extra_args: (amp: 0.005))
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
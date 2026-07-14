(
~n = 10;
~n.do { |i|SynthDef(("sine_" ++ i).asSymbol) {  }.add };
s.boot;
)

(
~n.do { |i|
	fork {
		var instr, proc;
		instr = SynthInstrument(("sine_" ++ i).asSymbol);
		proc = SoundProcess.create(("test_" ++ i).asSymbol, instr, [], 10);
		0.01.rand.wait;
		proc.startNewInstance((x: 0, y: i * 0.1), server_latency: 0.01, player_id: 0);
	}
}
)

AudioNodeOrder.nodes.collect(_.score_y)

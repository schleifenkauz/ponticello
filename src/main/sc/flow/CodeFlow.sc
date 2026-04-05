CodeFlow : AudioFlow {
	classvar synthdefctr = 0;
	var ugenGraph, synthdef;

	* new { |name, ugenGraph|
		var synthdef = "code_flow_%".format(synthdefctr);
		synthdefctr = synthdefctr + 1;
		^super.newCopyArgs(name, ugenGraph, synthdef);
	}

	createNode { |target, addAction|
		SynthDef(synthdef, ugenGraph).add;
		Server.local.sync;
		this.prCreateSynth(synthdef, [], target, addAction);
	}
}
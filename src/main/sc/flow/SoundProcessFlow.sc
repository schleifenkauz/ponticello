SoundProcessFlow : AudioFlow {
	var proc, <instance;

	* new { |name, proc| ^super.newCopyArgs(name, proc) }

	createNode { |target, addAction|
		var placement = (target: target, addAction: addAction);
		instance = proc.createInstance(pos: nil);
		instance.start(placement, latency: 0, playerId: -1, run: active);
		^instance.node
	}

	active_ { |enable, notify|
		if (instance.notNil) {
			instance.run(enable)
		};
		super.active_(enable, notify);
	}

	release {
		instance.release(latency: 0);
	}

	dispose {
	    instance = nil;
	}

	free {
		proc.free;
		super.free;
	}
}
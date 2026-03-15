ASRControl : ParameterControl {
	var attack, release;

	* new { |name, attack, release|
		^super.new(name).init(attack, release);
	}

	init { |att, rel|
		attack = att; release = rel;
	}

	getValue { |inst|
		var t = inst.current_time;
		^case
		{ t <= attack } { t / attack }
		{ t <= inst.def.duration - release } { 1 }
		{ t <= inst.def.duration } { (inst.def.duration - t) / release }
		{ 0 }
	}

	getUGen { |inst| nil }

	apply { |inst|
		inst.putArgument('attack', attack);
		inst.putArgument('release', release);
	}

	setAttack { |att|
		attack = att;
		this.updateInstances { |inst|
			inst.putArgument('attack', attack);
		}
	}

	setRelease { |rel|
		release = rel;
		this.updateInstances { |inst|
			inst.putArgument('release', release);
		}
	}
}
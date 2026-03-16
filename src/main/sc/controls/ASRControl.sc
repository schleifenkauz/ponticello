ASRControl : ParameterControl {
	var <attack, <release;

	* new { |attack, release|
		^super.new('attack-release').init(attack, release);
	}

	init { |att, rel|
		attack = ValueControl(\attack, att); release = ValueControl(\release, rel);
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

	prepare { |inst|
		inst.addControl(attack);
		inst.addControl(release);
	}

	attack_ { |att|
		attack.update(att);
	}

	release_ { |rel|
		release.update(rel);
	}
}
Instrument {
}


RoutineInstrument: Instrument {
	var func, parameterDefaults;

	* new {| func, parameterDefaults |
		^ super.new.init (parameterDefaults, func);
	}

	init {| defaults, f |
		parameterDefaults = defaults;
		func = f;
	}

	getDefaultValue {| param | ^ parameterDefaults[param]}


	create {| inst |
		^ Task {
			func.value (inst, inst.def.duration ? inf);
		}
	}
}

SynthInstrument: Instrument {
	var synthDefName, synthDesc;

	* new {| synthDef |
		^ super.new.init (synthDef);
	}

	init {| defName |
		synthDefName = defName;
		synthDesc = SynthDescLib.global.at (synthDefName);
	}

	getDefaultValue {| param | synthDesc.controlDict[param].defaultValue}

	create {| inst |
		var duration = inst.duration ? {| dur | dur - inst.cutoff};
		var args = List[duration: duration], synth;
		inst.extra_args.keysValuesDo {| p, v | args = args.addAll ([p, v] )};
		synth = Synth.newPaused (synthDefName, args, inst.node, \ addToTail);
		synth.onFree {inst.dispose};
	}
}

MIDIInstrument: Instrument {
	var vst;

	* new {| vst |
		^ super.new.init (vst);
	}

	init {| v | vst = v}

	create {| inst |
		var velocity = this.getControlValue (\ velocity) ? 64;
		var channel = this.getControlValue (\ channel) ? 0;
		var midinote = this.getControlValue (\ midinote);
		^ MIDINote (vst, midinote, velocity, channel);
	}
}

MIDINote {
	var vst, midinote, velocity, channel, playing;

	*
	new {
		| vst
		, midinote
		, velocity = 64
		, channel = 0 |
		^
		super
		.new.init(vst, midinote, velocity, channel)
	}

	init {
		| v
		, n
		, vel
		, c |
		vst = v;
		midinote = n;
		velocity = vel;
		channel = c;
	}

	run {
		| active
		, inst |
		if (playing != active) {
			playing = active;
			if (active) {
				vst.midi.noteOn(channel, midinote, velocity);
				if (inst.notNil && inst.
					def.duration.notNil
				)
				{
					TempoClock.sched(inst.server_latency + inst.
						def.duration - inst.cutoff
					)
					{
						this.run(false);
						inst.dispose;
					}
				}
			}
			{
				vst.midi.noteOff(channel, midinote);
			}
		}
	}

	set {
		| parameter
		, value |
		vst.set(parameter, value);
	}

	map {
		| parameter
		, bus |
		vst.map(parameter, bus);
	}

}

+Task {
	run {
		| active
		, inst |
		if (active) {
			this.play;
			if (inst.notNil && inst.
				def.duration.notNil
			)
			{
				TempoClock.sched(inst.
					def.duration - inst.cutoff
				)
				{
					this.run(false);
					inst.dispose;
				}
			}
		}
		{
			this.pause;
		}
	}

	release {
		this.stop;
	}
}
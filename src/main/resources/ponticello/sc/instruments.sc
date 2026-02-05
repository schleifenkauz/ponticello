Instrument {
	getDefaultValue { |param| ^nil }
}


RoutineInstrument : Instrument {
	var func, parameterDefaults;

	* new {| func, parameterDefaults |
		^ super.new.init (parameterDefaults, func);
	}
	
	type { ^\routine }

	init {| defaults, f |
		parameterDefaults = defaults;
		func = f;
	}

	getDefaultValue {| param | ^parameterDefaults[param]}

	create {| inst |
		^Task {
			func.value(inst, inst.def.duration ? inf);
		}
	}
}

SynthInstrument : Instrument {
	var synthDefName, synthDesc;

	* new {| synthDef |
		^super.new.init (synthDef);
	}

	type { ^\synth }

	init {| defName |
		synthDefName = defName;
		synthDesc = SynthDescLib.global.at(synthDefName);
    }

	getDefaultValue {| param | ^synthDesc.controlDict[param] !? { |ctrl| ctrl.defaultValue }}

	create {| inst |
		var duration = inst.def.duration !? {| dur | dur - inst.cutoff};
		var args = List[duration: duration], synth;
		if (duration == inf || duration == nil) {
			args.addAll([\auto_release, 0]);
		};
		inst.getInitialArguments.keysValuesDo {| p, v | args = args.addAll([p, v]) };
		synth = Synth.newPaused(synthDefName, args, inst.node, \addToTail);
		//synth.register(assumePlaying: true);
		synth.onFree { inst.dispose };
		^synth
	}
}

MIDIInstrument : Instrument {
	var vst;
	classvar reserved_names;

	*initClass {
		reserved_names = Set['velocity', 'channel', 'midinote'];
	}

	* new {| vst |
		^super.new.init (vst);
	}

	type { ^\vst_midi }

	init {| v | vst = v}

	create {| inst |
		var velocity = inst.getControlValue(\velocity) ? 64;
		var channel = inst.getControlValue(\channel) ? 0;
		var midinote = inst.getControlValue(\midinote);
		inst.getInitialArguments.keysValuesDo { |p, v| 
			if (reserved_names.includes(p).not) {
				vst.set(p, v);
			}
		};
		^MIDINote(vst, midinote, velocity, channel);
	}
}

MIDINote {
	var vst, midinote, velocity, channel, playing;

	*
	new {| vst, midinote, velocity = 64, channel = 0 |
		^super.new.init(vst, midinote, velocity, channel)
	}

	init {| v, n, vel, c |
		vst = v;
		midinote = n;
		velocity = vel;
		channel = c;
	}

	run { | active, inst |
		if (playing != active) {
			playing = active;
			if (active) {
				TempoClock.sched(inst.server_latency + 0.001) { //minimal offset to ensure that not on occurs after note off
					vst.midi.noteOn(channel, midinote, velocity);
				};
				if (inst.notNil && inst.def.duration.notNil) {
					TempoClock.sched(inst.server_latency + inst.def.duration - inst.cutoff) {
						this.run(false);
						inst.dispose;
					}
				}
			} {
				vst.midi.noteOff(channel, midinote);
			}
		}
	}

	set {| parameter, value |
		vst.set(parameter, value);
	}

	map {| parameter, bus |
		vst.map(parameter, bus);
	}

}

+Task {
	run { | active, inst |
		if (active) {
			this.play;
			if (inst.notNil && inst.def.duration.notNil) {
				TempoClock.sched(inst.def.duration - inst.cutoff) {
					this.run(false);
					inst.dispose;
				}
			}
		} { this.pause; }
	}

	release {
		this.stop;
	}
}
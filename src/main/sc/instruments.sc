Instrument {
	getDefaultValue { |param| ^nil }
}


RoutineInstrument : Instrument {
	var name, func, parameterDefaults;
	classvar dict;

	* initClass {
		dict = Dictionary.new;
	}

	* new {| name, parameterDefaults, func |
		^if (dict.includesKey(name)) {
			var instr = dict[name];
			instr.update(parameterDefaults, func)
		} {
			var instr = super.newCopyArgs(name, func, parameterDefaults);
			dict[name] = instr;
			instr
		}
	}

	* remove { |name| dict.remove(name) }

	* get { |name| ^dict[name] }

	update { |defaults, fn|
		parameterDefaults = defaults;
		func = fn;
	}

	type { ^\routine }

	getDefaultValue {| param | ^parameterDefaults[param]}

	create {| inst | ^RoutineInstance(inst, func) }

	asString { ^"Routine %".format(name) }
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
		var duration = inst.def.duration !? {| dur | dur - inst.cutoff}, args, synth;
		if (duration == inf || duration == nil) {
            args.addAll([\auto_release, 0]);
            duration = 0.1;
        };
		args = List[duration: duration];
		inst.getInitialArguments.keysValuesDo {| p, v | args = args.addAll([p, v]) };
		//postf("Creating synth % with args %\n", synthDefName, args);
		synth = Synth.newPaused(synthDefName, args, inst.node, \addToTail);
		//synth.register(assumePlaying: true);
		synth.onFree {
		    //postf("Synth % freed, disposing instance\n", synth);
		    inst.dispose;
        };
		^synth
	}

	asString { ^"SynthDef %".format(synthDefName) }
}

MIDIInstrument : Instrument {
	var vst;
	classvar reserved_names;

	*initClass {
		reserved_names = Set['velocity', 'channel', 'midinote'];
	}

	* new {| vst |
		^super.new.init(vst);
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

	asString { ^"VST MIDI: %".format(vst.info.name) }
}

MIDINote {
	var vst, midinote, velocity, channel, playing, instance;

	* new {| vst, midinote, velocity = 64, channel = 0, instance |
		^super.new.init(vst, midinote, velocity, channel, instance)
	}

	init {| v, n, vel, c, inst |
		vst = v;
		midinote = n;
		velocity = vel;
		channel = c;
		instance = inst;
	}

	run { | active |
		if (playing != active) {
			playing = active;
			if (active) {
				SystemClock.sched(instance.server_latency + 0.001) { //minimal offset to ensure that not on occurs after note off
					vst.midi.noteOn(channel, midinote, velocity);
				};
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

    release {
        vst.midi.noteOff(channel, midinote);
        instance.dispose;
    }
}

RoutineInstance {
    var task, instance;

    * new { |instance, func|
		var task = Task({
			try {
				func.value(instance, instance.def.duration ? inf)
			} { |error|
				error.reportError;
			};
			instance.dispose;
		}, SystemClock);
         ^super.newCopyArgs(task, instance);
    }

    run { | active |
	    if (active) { task.play } { task.pause }
    }

    release {
        task.stop;
        instance.dispose;
    }
}
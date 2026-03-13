Instrument {
	getDefaultValue { |param| ^nil }
}


RoutineInstrument : Instrument {
	var name, func, onFinished, parameterDefaults;
	classvar dict;

	* initClass {
		dict = Dictionary.new;
	}

	* new {| name, parameterDefaults, func, onFinished |
		^if (dict.includesKey(name)) {
			var instr = dict[name];
			instr.update(parameterDefaults, func, onFinished)
		} {
			var instr = super.newCopyArgs(name, func, onFinished, parameterDefaults);
			dict[name] = instr;
			instr
		}
	}

	* remove { |name| dict.remove(name) }

	* get { |name| ^dict[name] }

	update { |defaults, fn, finished|
		parameterDefaults = defaults;
		func = fn;
		onFinished = finished;
	}

	type { ^\routine }

	getDefaultValue {| param | ^parameterDefaults[param]}

	create {| inst | ^RoutineInstance(inst, func, onFinished) }

	asString { ^"Routine %".format(name) }
}

SynthInstrument : Instrument {
	var synthDefName, synthDesc;
	classvar byName;

	* initClass {
		byName = Dictionary.new;
	}

	* new {| synthDef |
		^byName[synthDef] ?? {
			var desc = SynthDescLib.global.at(synthDef);
			var instr = super.newCopyArgs(synthDef, desc);
			byName[synthDef] = instr;
			^instr
		};
	}

	type { ^\synth }

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
    var func, instance, onFinished, env, task;

    * new { |instance, func, onFinished|
		var env = EnvironmentRedirect(currentEnvironment);
         ^super.newCopyArgs(func, instance, onFinished, env).prCreateTask;
    }

	prCreateTask {
		task = Task({
			try {
				env.use {
					func.value(instance, instance.def.duration ? inf)
				}
			} { |error|
				error.reportError;
			};
			fork { this.prFinished }
		}, SystemClock);
	}

	prFinished {
		protect {
			env.use {
				postf("Running on finished %\n", onFinished);
				onFinished.value(instance);
			};
		} {
			instance.dispose;
		}
	}

    run { | active |
	    if (active) { task.play } { task.pause }
    }

    release {
        task.stop;
		this.prFinished;
    }
}
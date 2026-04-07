MidiEffectInstrument : NamedObject {
	classvar dict;
	var parameterDefaults, <start, <stop, <noteOn, <noteOff, <control;

	* dict { ^dict ?? {dict = Dictionary.new} }

	* new { |name, defaults, start, stop, noteOn, noteOff, cc|
		^super.newCopyArgs(name, defaults, start, stop, noteOn, noteOff, cc);
	}

	update { |defaults, strt, stp, on, off, cc|
		parameterDefaults = defaults;
		start = strt; stop = stp; //TODO restart tasks
		noteOn = on; noteOff = off; control = cc;
	}

	type { \routine }

	getDefaultValue { |param| ^parameterDefaults[param] }

	asString { ^"MidiEffect %".format(name) }
}

MidiEffect : MidiInstrument {
	var procName, instr, <instance, env, <enabled = true, task, track;

	* create { |name, instr, controls, enabled|
		var env = EnvironmentRedirect(currentEnvironment);
		var proc = SoundProcess.create(name, instr, controls);
		var instance = SoundProcessInstance.new(proc, 0, nil, 0, ());
		^super.newCopyArgs(name, instr, instance, env, enabled);
	}

	noteOn { |num, val, src|
		^if (enabled) {
			try {
				var mySrc = src.copy.instr_(this);
				//postf("Note On %, %. Track: %, src: %", num, val, track, mySrc);
				//instanc.postln;
				env.use { instr.noteOn.value(num, val, track, instance, mySrc) };
			} { |error|
				postf("Error during noteOn handling of MidiEffect % (%, %)\n", instr.name, num, val);
				error.reportError;
				false;
			}
		} { true }
	}

	noteOff { |num, val, src|
		^if (enabled) {
			try {
                var mySrc = src.copy.instr_(this);
				env.use { instr.noteOff.value(num, val, track, instance, mySrc) };
			} { |error|
				postf("Error during noteOff handling of MidiEffect % (%, %)\n", instr.name, num, val);
				error.reportError;
				true;
			}
		} { true }
	}

	control { |num, val, src|
		^if (enabled) {
			try {
				var mySrc = src.copy.instr_(this);
				^env.use { instr.control.value(num, val, track, instance, mySrc) };
			} { |error|
				postf("Error during cc handling of MidiEffect % (%, %)\n", instr.name, num, val);
				error.reportError;
				false;
			}
		} { true }
	}

	prCreateTask {
		task = Task {
			env.use {
				instr.start.value(track, instance)
			};
			this.prCreateTask;
		};
	}

	activate { |tr|
		track = tr;
		this.prCreateTask;
		if (enabled) {
			task.play;
		}
	}

	pause {
		task.pause;
		env.use { instr.stop.value(track, instance) };
		track.stopEffect(this);
	}

	enabled_ { |v|
		enabled = v;
		if (v) {
			task.play;
		} {
			this.pause;
		}
 	}


	dispose {
		if (enabled) {
			this.pause;
		}
	}

	allNotesOff {} //TODO
}
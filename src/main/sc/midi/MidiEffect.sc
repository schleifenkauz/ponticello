MidiEffectInstrument {
	classvar dict;
	var <>name, parameterDefaults, <start, <stop, <noteOn, <noteOff, <control;

	* initClass { dict = Dictionary.new }

	* new { |name, defaults, start, stop, noteOn, noteOff, cc|
		^if (dict.includesKey(name)) {
			var instr = dict[name];
			instr.update(defaults, start, stop, noteOn, noteOff, cc);
		} {
			var instr = super.newCopyArgs(name, defaults, start, stop, noteOn, noteOff, cc);
			dict[name] = instr;
			instr;
		}
	}

	* get { |name| ^dict[name] }

	* remove { |name| dict.removeAt(name) }

	* rename { |old_name, new_name|
		var instr = dict.removeAt(old_name);
		if (instr.notNil) {
			instr.name = new_name;
			dict[new_name] = instr;
		} {
			Exception("MidiEffectInstrument % not found".format(old_name)).throw;
		}
	}

	update { |defaults, strt, stp, on, off, cc|
		parameterDefaults = defaults;
		start = strt; stop = stp; //TODO restart tasks
		noteOn = on; noteOff = off; control = cc;
	}

	type { \routine }

	getDefaultValue { |param| ^parameterDefaults[param] }

	asString { "MidiEffect: %".format(name) }
}

MidiEffect {
	var procName, instr, controls, env, <enabled = true, task, track;

	* create { |name, instr, controls, enabled|
		var env = EnvironmentRedirect(currentEnvironment);
		var proc = SoundProcess.create(name, instr, controls);
		controls = SoundProcessInstance.new(proc, 0, nil, 0, ());
		^super.newCopyArgs(name, instr, controls, env, enabled);
	}

	noteOn { |num, val, chan, track, src|
		postf("Note on %, %, %. Enabled: %\n", num, val, chan, enabled);
		^if (enabled) {
			try {
				var mySrc = (latency: src.latency, player_id: src.player_id, instr: this);
				env.use { instr.noteOn.value(num, val, chan, track, controls, mySrc) };
			} { |error|
				//postf("Error during noteOn handling of MidiEffect % (%, %)\n", instr.name, num, val);
				error.reportError;
				false;
			}
		} { true }
	}

	noteOff { |num, val, chan, track, src|
		^if (enabled) {
			try {
				var mySrc = (latency: src.latency, player_id: src.player_id, instr: this);
				env.use { instr.noteOff.value(num, val, chan, track, controls, mySrc) };
			} { |error|
				//postf("Error during noteOff handling of MidiEffect % (%, %)\n", instr.name, num, val);
				error.reportError;
				true;
			}
		} { true }
	}

	control { |num, val, chan, track, src|
		^if (enabled) {
			try {
				var mySrc = (latency: src.latency, player_id: src.player_id, instr: this);
				^env.use { instr.control.value(num, val, chan, track, controls, mySrc) };
			} { |error|
				//postf("Error during cc handling of MidiEffect % (%, %)\n", instr.name, num, val);
				error.reportError;
				false;
			}
		} { true }
	}

	prCreateTask {
		task = Task {
			env.use {
				instr.start.value(track, controls)
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

	dispose {
		task.stop;
		env.use { instr.stop.value(track, controls) };
	}

	enabled_ { |v|
		enabled = v;
		if (v) {
			task.play;
		} {
			task.pause;
		}
 	}

	allNotesOff {} //TODO
}
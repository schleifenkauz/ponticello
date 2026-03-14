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
		start = strt; stop = stp;
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

	noteOn { |chan, num, val, track, src|
		^if (enabled) {
			var mySrc = (latency: src.latency, player_id: src.player_id, instr: this);
			env.use { instr.noteOn.value(num, val, chan, track, controls, mySrc) };
		} { true }
	}

	noteOff { |chan, num, val, track, src|
		var mySrc = (latency: src.latency, player_id: src.player_id, instr: this);
		^env.use { instr.noteOff.value(num, val, chan, track, controls, mySrc) };
	}

	control { |chan, num, val, track, src|
		if (enabled) {
			var mySrc = (latency: src.latency, player_id: src.player_id, instr: this);
			^env.use { instr.control.value(num, val, chan, track, controls, mySrc) };
		}
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
		if (v) {
			task.play;
		} {
			task.pause;
		}
 	}

	allNotesOff {}
}
RoutineInstrument : Instrument { //TODO common superclass with MidiEffectInstrument
	var <>name, func, onFinished, parameterDefaults;
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

	* remove { |name| dict.removeAt(name) }

	* get { |name| ^dict[name] }

	* rename { |old_name, new_name|
		var instr = dict.removeAt(old_name);
		if (instr.isNil) {
			Exception("RoutineInstrument % not found".format(old_name)).throw;
		};
		instr.name = new_name;
		dict[new_name] = instr;
	}

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

    release { |latency=0|
		SystemClock.sched(latency) {
			task.stop;
			this.prFinished;
		}
    }
}
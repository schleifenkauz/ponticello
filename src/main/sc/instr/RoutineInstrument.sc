RoutineInstrument : NamedObject {
	var func, onFinished, parameterDefaults;
	classvar dict;

	* dict { ^dict ?? { dict = Dictionary.new } }

	* new {| name, parameterDefaults, func, onFinished |
		^super.newCopyArgs(name, parameterDefaults, func, onFinished);
	}

	update { |defaults, fn, finished|
		parameterDefaults = defaults;
		func = fn;
		onFinished = finished;
	}

	type { ^\routine }

	isAutoRelease { ^false }

	getDefaultValue {| param | ^parameterDefaults[param]}

	create {| inst | ^RoutineInstance(inst, func, onFinished) }

	generateScore { |inst|
		var delta, env = EnvironmentRedirect(currentEnvironment), score;
		var routine = Routine { env.use { func.(inst, inst.def.duration) } };
		fork {
			inst.clock_time = 0.0;
			while { (delta = routine.next ).notNil && (inst.clock_time < inst.def.duration) } {
				if (delta.isKindOf(Number)) {
					inst.clock_time = inst.clock_time + delta;
				} {
					if (delta != \hang) {
						delta.wait; //Conditions etc.
					}
				}
			};
			score = inst.children.collect { |child|
				[child.pos.t, child.def.name/*TODO: extra_controls*/]
			};
			env.use { onFinished.value(inst) };
			inst.clock_time = nil;
			Ponticello.sendMsg('/generated_score', inst.def.name, *score.flatten.postln);
		}
	}

	asString { ^"Routine %".format(name) }
}

RoutineInstance {
    var func, instance, onFinished, env, task;

    * new { |instance, func, onFinished|
		//TODO this means it can't put new variables in the global namespace...
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
		}, SystemClock);
	}

	prFinished {
		protect {
			env.use {
				//postf("Running on finished %\n", onFinished);
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
			postf("Stopping %, %\n", instance, task);
			task.stop;
			this.prFinished;
		}
    }
}
RoutineInstrument : NamedObject {
	classvar dict;
	var parameterDefaults, func;

	* dict { ^dict ?? { dict = Dictionary.new } }

	* new {| name, parameterDefaults, func |
		^super.newCopyArgs(name, parameterDefaults, func);
	}

	update { |defaults, fn|
		parameterDefaults = defaults;
		func = fn;
	}

	type { ^\routine }

	isAutoRelease { ^false }

	getDefaultValue {| param | ^parameterDefaults[param]}

	create {| inst | ^RoutineInstance(inst, func) }

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
			inst.clock_time = nil;
			Ponticello.sendMsg('/generated_score', inst.def.name, *score.flatten.postln);
		}
	}
}

RoutineInstance {
    var func, instance, task;

    * new { |instance, func|
		//TODO this means it can't put new variables in the global namespace...
		var env = EnvironmentRedirect(currentEnvironment);
        var task = Task({
			try {
				func.value(instance, instance.def.duration ? inf)
			} { |error|
				error.reportError;
			};
		}, SystemClock);
		^super.newCopyArgs(func, instance, task);
    }

    run { | active |
	    if (active) { task.play } { task.pause }
    }

    release { |latency=0|
		SystemClock.sched(latency) {
			postf("Stopping %, %\n", instance, task);
			task.stop;
			instance.dispose;
		}
    }
}
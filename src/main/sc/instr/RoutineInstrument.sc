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
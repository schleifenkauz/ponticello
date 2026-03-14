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
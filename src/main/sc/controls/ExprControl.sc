ExprControl : ParameterControl {
	var func;

	* new { |name, func|
		^super.new(name).init(func);
	}

	init { |f| func = f }

	getValue { |inst| ^func.value(inst) }

	getUGen { |inst| ^this.getValue(inst) }

	prepare { |inst|
	    var value = func.value(inst, 0);
		inst.putArgument(this.name, value);
	}

	update { |new_func|
		func = new_func;
		if (this.sound_proc.type != \routine) {
			this.updateInstances { |inst|
				inst.putArgument(func.value(inst));
			}
		}
	}
}

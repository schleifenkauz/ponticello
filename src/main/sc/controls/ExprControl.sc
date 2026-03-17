ExprControl : ParameterControl {
	var func;

	* new { |name, func|
		^super.new(name).init(func);
	}

	init { |f| func = f }

	getValue { |inst| ^func.value(inst) }

	getUGen { |inst| ^this.getValue(inst) }

	getSynthArgument { |inst| ^func.value(inst) }

	update { |new_func|
		func = new_func;
		if (this.sound_proc.type != \routine) {
			this.updateInstances { |inst|
				inst.set(func.value(inst));
			}
		}
	}

	asString { ^"%: Expr".format(this.name) }
}

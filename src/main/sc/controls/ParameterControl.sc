ParameterControl {
	var <>name, <>sound_proc;

	* new { |n|
		^super.new.name_(n);
	}

	allocatesBus { ^false }

	getBus { |inst| ^if (this.allocatesBus) { inst.getControlBus(this.name) } { nil } }

	updateInstances { |func| sound_proc.updateInstances(this, func) }

	getValue { |inst| ^nil }

	getUGen { |inst| ^nil }

	getSynthArgument { |inst| ^nil }

	prepare { |inst| }

	apply { |inst| }

	dispose { }
}


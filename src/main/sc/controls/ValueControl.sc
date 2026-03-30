ValueControl : ParameterControl {
	var value, allocateBus, cutoff_multiplier, bus;

	* new { |name, value, allocate_bus = false, cutoff_multiplier = 0|
		^super.new(name).init(value, allocate_bus, cutoff_multiplier)
	}

	init { |val = 0, use_bus = false, multiplier = 0|
		value = val;
		allocateBus = use_bus;
		cutoff_multiplier = multiplier;
		if (allocateBus && (cutoff_multiplier == 0)) {
			bus = Bus.control;
		}
	}

	getBus { |inst| ^bus ?? { super.getBus(inst) } }

	getValue { |inst|
		^if (cutoff_multiplier == 0) { value } { value + (cutoff_multiplier * inst.cutoff) }
	}

	getUGen { |inst|
		^if (allocateBus) {
			if (bus != nil) { bus.kr } { inst.getControlBus(this.name).kr };
		} { value }
	}

	allocatesBus { ^allocateBus && (cutoff_multiplier != 0) }

	setAllocateBus { |allocate|
		if (allocateBus != allocate) {
			allocateBus = allocate;
			if (allocate) {
				if (cutoff_multiplier == 0) {
					bus = Bus.control;
					this.updateInstances { |inst|
						inst.mapParameter(this.name, bus);
					}
				} {
					this.updateInstances { |inst|
						var bus = inst.createControlBus(this.name, value);
						inst.mapParameter(this.name, bus);
					}
				}
			} {
				this.updateInstances { |inst|
					inst.set(this.name, value); //unmap
				};
				if (cutoff_multiplier == 0) {
					bus.free; bus = nil;
				} {
					updateInstances { |inst|
						inst.freeControlBus(this.name);
					}
				}
			}
		};
	}

	getSynthArgument { |inst|
	    ^if (this.allocatesBus) {
	        (bus ? inst.getControlBus(this.name)).asMap
        } { this.getValue(inst) }
    }

	prepare { |inst|
		if (this.allocatesBus) {
			var initial_value = this.getValue(inst);
			inst.createControlBus(this.name, initial_value);
		}
	}

	update { |val|
		value = val;
		if (allocateBus) {
			if (bus != nil) { bus.set(value); } {
				this.updateInstances { |inst|
					var val = value + (cutoff_multiplier * inst.cutoff);
					inst.getControlBus(this.name).set(val);
				};
			};
		} {
			if (this.sound_proc.notNil && (this.sound_proc.type != \routine)) {
				this.updateInstances { |inst|
					inst.set(this.name, value);
				}
			}
		}
	}

	dispose {
		if (bus != nil) { bus.free; }
	}

	asString { ^"%: % (allocate: %)".format(this.name, value, allocateBus) }
}

+ Association {
	name { ^this.key }

	getValue { ^this.value }

	getUGen { ^this.value }

	getBus { ^nil }

	getSynthArgument { ^this.value }

	prepare {  }

	apply {}

	dispose {  }

	sound_proc {}

	sound_proc_ {}
}
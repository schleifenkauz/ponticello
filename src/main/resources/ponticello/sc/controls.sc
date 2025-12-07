ParameterControl {
	var <name, <sound_proc;

	initialize { |proc, param_name|
		sound_proc = proc;
		name = param_name;
	}

	allocatesBus { false }

	getBus { |inst| if (this.allocatesBus) { inst.getControlBus(this) } { nil } }

	updateInstances { |func| sound_proc.updateInstances(func) }

	getValue { |inst, t| }

	getUGen { |inst| }

	prepare { |inst| }

	apply { |inst| }

	dispose { }
}

ValueControl : ParameterControl {
	var value, allocateBus, cutoff_multiplier, bus;

	* new { |value = 0, allocate_bus = false, cutoff_multiplier = 0|
		^super.new.init(value, allocate_bus, cutoff_multiplier)
	}

	init { |val = 0, use_bus = false, multiplier = 0|
		value = val;
		allocateBus = use_bus;
		cutoff_multiplier = multiplier;
	}

	getBus { |inst| bus ? super.getBus(inst) }

	getValue { |inst, t| value + (cutoff_multiplier * inst.cutoff) }

	getUGen { |inst|
		^if (allocateBus) {
			if (bus != nil) { bus.kr } { inst.getControlBus(this.name).kr };
		} { value }
	}

	initialize { |proc, name|
		super.initialize(proc, name);
		if (allocateBus && (cutoff_multiplier == 0)) {
			bus = Bus.control;
		}
	}

	allocatesBus { allocateBus && (cutoff_multiplier != 0) }

	apply { |inst|
		if (inst.type == \synth) {
			inst.putArgument(this.name, value);
		};
	}

	update { |value|
		if (allocateBus) {
			if (bus != nil) { bus.set(value); } {
				this.updateInstances { |inst|
					var val = value + (cutoff_multiplier * inst.cutoff);
					inst.getControlBus(this.name).set(val);
				};
			};
		} {
			if (this.sound_proc.type == \synth) {
				this.updateInstances { |inst|
					inst.putArgument(this.name, value);
				}
			}
		}
	}

	dispose {
		if (bus != nil) { bus.free; }
	}
}

BusControl : ParameterControl {
	var bus;

	* new { |bus|
		^super.new.init(bus)
	}

	init { |b| bus = b  }

	getBus { bus }

	getValue { |inst, t| bus.getSynchronous }

	getUGen { |inst| bus.kr }

	apply { |inst|
		inst.mapParameter(this.name, bus);
	}

	update { |new_bus|
		bus = new_bus;
		updateInstances { |inst|
			inst.mapParameter(this.name, bus);
		}
	}
}

EnvelopeControl : ParameterControl {
	classvar synth_def_ctr = 0;
	var env, synth_def;

	* new { |env|
		^super.new.init(env)
	}

	init { |e| env = e }

	initialize { |proc, name|
		super.initialize(proc, name);
		synth_def = ("env" ++ synth_def_ctr).asSymbol;
		synth_def_ctr = synth_def_ctr + 1;
		this.defineSynth;
	}

	allocatesBus { true }

	defineSynth {
		SynthDef(synth_def) { |out, cutoff = 0|
			var sig = IEnvGen.kr(env, index: Sweep.kr(rate: ~time_warp_bus.kr));
			Out.kr(out, sig);
		}.add;
	}

	getValue { |inst, t| ^env.at(t) }

	getUGen { |inst| ^inst.getControlBus(super.name).kr }

	prepare { |inst|
		var initial_value = env.at(inst.cutoff);
		inst.createControlBus(this.name, initial_value);
	}

	apply { |inst|
		if (inst.type == \synth) {
			var bus = inst.getControlBus(this.name);
			inst.createAuxilSynth(this.name, synth_def, [out: bus, cutoff: inst.cutoff]);
			inst.mapParameter(this.name, bus);
		}
	}

	update { |new_env|
		env = new_env;
		if (this.sound_proc.type == \synth) {
			this.defineSynth;
			this.updateInstances { |inst|
				var bus = inst.getControlBus(this.name);
				inst.replaceAuxilSynth(this.name, synth_def, [out: bus, cutoff: inst.cutoff])
			};
		}
	}


	dispose {
		if (this.sound_proc.type == \synth) {
			this.updateInstances { |inst|
				inst.freeAuxilSynth(this.name);
			}
		}
	}
}

LFOControl : ParameterControl {
	classvar synth_def_ctr = 0;
	var references, ugen_func, synth_def;

	* new { |references, ugen_func|
		^super.new.init(references, ugen_func);
	}

	init { |refs, func|
		references = refs;
		ugen_func = func
	}

	initialize { |proc, name|
		super.initialize(proc, name);
		synth_def = ("lfo" ++ synth_def_ctr).asSymbol;
		synth_def_ctr = synth_def_ctr + 1;
		this.defineSynth;
	}

	allocatesBus { true }

	defineSynth {
		SynthDef(synth_def) { |out, cutoff|
			var sig = ugen_func.value(cutoff);
			Out.kr(out, sig);
		}.add;
	}

	getUGen { |inst| ^inst.getControlBus(super.name).kr }

	getValue { |inst| ^inst.getControlBus(super.name).getSynchronous }

	prepare { |inst|
		inst.createControlBus(this.name);
	}

	apply { |inst|
		var bus = inst.getControlBus(this.name);
		this.prCreateSynth(inst, bus, replace: false);
		inst.mapParameter(this.name, bus);
	}

	prCreateSynth { |inst, bus, replace|
		var args = [out: bus, cutoff: inst.current_time];
		var synth = if (replace) {
			inst.replaceAuxilSynth(this.name, synth_def, args);
		} {
			inst.createAuxilSynth(this.name, synth_def, args);
		};
		references.do { |ref|
			var b = inst.def.getControl(ref).getBus(inst);
			if (b != nil) {
				synth.map(ref, b);
			} {
				synth.set(ref, inst.getControlValue(ref));
			}
		}
	}

	update { |func|
		ugen_func = func;
		this.defineSynth;
		this.updateInstances { |inst|
			var bus = inst.getControlBus(this.name);
			this.prCreateSynth(inst, bus, replace: true);
		}
	}

	dispose {
		this.updateInstances { |inst|
			inst.freeAuxilSynth(this.name);
		}
	}
}

ExprControl : ParameterControl {
	var func;

	* new { |func|
		^super.new.init(func);
	}

	init { |f| func = f }

	getValue { |inst, t| func.value(inst, t) }

	getUGen { |inst, t| this.getValue(inst, t) }

	apply { |inst|
		inst.putArgument(func.value(inst, 0));
	}

	update { |new_func|
		func = new_func;
		if (this.sound_process.type == \synth) {
			this.updateInstances { |inst|
				inst.putArgument(func.value(inst, inst.current_time));
			}
		}
	}
}
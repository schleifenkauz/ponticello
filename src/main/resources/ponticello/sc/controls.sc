ParameterControl {
	var <>name, <>sound_proc;

	* new { |n|
		^super.new.name_(n);
	}

	allocatesBus { ^false }

	getBus { |inst| ^if (this.allocatesBus) { inst.getControlBus(this.name) } { nil } }

	updateInstances { |func| sound_proc.updateInstances(func) }

	getValue { |inst| ^nil }

	getUGen { |inst| ^nil }

	prepare { |inst| }

	apply { |inst| }

	dispose { }
}

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

	getBus { |inst| ^bus ? super.getBus(inst) }

	getValue { |inst| ^value + (cutoff_multiplier * inst.cutoff) }

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
					inst.putArgument(this.name, value); //unmap
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

	apply { |inst|
		if (inst.type == \synth) {
			inst.putArgument(this.name, value);
		};
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

	* new { |name, bus|
		^super.new(name).init(bus)
	}

	init { |b| bus = b }

	getBus { ^bus }

	getValue { |inst| ^bus.getSynchronous }

	getUGen { |inst| ^bus.kr }

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

	* new { |name, env|
		^super.new(name).init(env)
	}

	init { |e|
		env = e;
		synth_def = ("env_" ++ name ++ synth_def_ctr).asSymbol;
		synth_def_ctr = synth_def_ctr + 1;
		this.defineSynth;
	}

	allocatesBus { ^true }

	defineSynth {
		SynthDef(synth_def) { |out, cutoff = 0|
			var sig = IEnvGen.kr(env, index: cutoff + Sweep.kr(rate: ~time_warp_bus.kr));
			Out.kr(out, sig);
		}.add;
	}

	getValue { |inst| ^env.at(inst.current_time) }

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

	* new { |name, references, ugen_func|
		^super.new(name).init(references, ugen_func);
	}

	init { |refs, func|
		references = refs;
		ugen_func = func;
		synth_def = ("lfo_" ++ name ++ synth_def_ctr).asSymbol;
		synth_def_ctr = synth_def_ctr + 1;
		this.defineSynth;
	}

	allocatesBus { ^true }

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
			var ctrl = inst.getControl(ref);
			var b = ctrl.getBus(inst);
			if (b.notNil) {
				synth.map(ref, b);
			} {
				synth.set(ref, ctrl.getValue(inst));
			}
		}
	}

	update { |refs, func|
		references = refs;
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

	* new { |name, func|
		^super.new(name).init(func);
	}

	init { |f| func = f }

	getValue { |inst| ^func.value(inst.current_time) }

	getUGen { |inst| ^this.getValue(inst) }

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

ASRControl : ParameterControl {
	var attack, release;

	* new { |name, attack, release|
		^super.new(name).init(attack, release);
	}

	init { |att, rel|
		attack = att; release = rel;
	}

	getValue { |inst|
		var t = inst.current_time;
		^case
		{ t <= attack } { t / attack }
		{ t <= inst.def.duration - release } { 1 }
		{ t <= inst.def.duration } { (inst.def.duration - t) / release }
		{ 0 }
	}

	getUGen { |inst| nil }

	apply { |inst|
		inst.putArgument('attack', attack);
		inst.putArgument('release', release);
	}

	setAttack { |att|
		attack = att;
		this.updateInstances { |inst|
			inst.putArgument('attack', attack);
		}
	}

	setRelease { |rel|
		release = rel;
		this.updateInstances { |inst|
			inst.putArgument('release', release);
		}
	}
}
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
		Server.local.sync;
	}

	getUGen { |inst| ^inst.getControlBus(super.name).kr }

	getValue { |inst| ^inst.getControlBus(super.name).getSynchronous }

	prepare { |inst|
		inst.createControlBus(this.name);
	}

	getSynthArgument { |inst| ^inst.getControlBus(this.name).asMap }

	apply { |inst|
		var bus = inst.getControlBus(this.name);
		if (inst.restarting.not) {
			this.prCreateSynth(inst, bus, \createAuxilSynth);
		};
	}

	prCreateSynth { |inst, bus, method|
		var args = List[out: bus, cutoff: inst.current_time];
		references.do { |ref|
			var ctrl = inst.getControl(ref);
			if (ctrl.notNil) {
				args.addAll([ref, ctrl.getSynthArgument(inst)]);
			} {
				postf("WARNING: Could not resolve control % on SoundProcess %\n", ref, inst.def.name);
			}
		};
		inst.perform(method, this.name, synth_def, args);
	}

	update { |refs, func|
		references = refs;
		ugen_func = func;
		this.defineSynth;
		this.updateInstances { |inst|
			var bus = inst.getControlBus(this.name);
			this.prCreateSynth(inst, bus, \replaceAuxilSynth);
		}
	}

	dispose {
		this.updateInstances { |inst|
			inst.freeAuxilSynth(this.name);
		}
	}

	asString { ^"%: LFO".format(name) } //TODO print compiled string
}

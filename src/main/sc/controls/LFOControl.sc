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
			this.prCreateSynth(inst, bus, replace: false);
		};
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
			if (ctrl.notNil) {
				var b = ctrl !? {ctrl.getBus (inst)};
				if (b.notNil) {
					synth.map (ref, b);
				} {
					synth.set (ref, ctrl.getValue (inst) );
				}
			} {
				postf("Could not resolve control % on SoundProcess %\n", ref, inst.def.name);
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

	asString { ^"%: UGen".format(name) } //TODO print compiled string
}

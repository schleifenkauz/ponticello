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
			var sig = IEnvGen.kr(env, index: cutoff + PonticelloPlayback.sweep);
			Out.kr(out, sig);
		}.add;
	}

	getValue { |inst| ^env.at(inst.current_time) }

	getUGen { |inst| ^inst.getControlBus(super.name).kr }

	prepare { |inst|
		var initial_value = env.at(inst.current_time);
		inst.createControlBus(this.name, initial_value);
	}

	getSynthArgument { |inst| ^inst.getControlBus(this.name).asMap }

	apply { |inst|
		if (inst.type != \routine) {
			var bus = inst.getControlBus(this.name);
			if (inst.restarting.not) {
				inst.createAuxilSynth(this.name, synth_def, [out: bus, cutoff: inst.cutoff]);
			};
		}
	}

	update { |new_env|
		env = new_env;
		if (this.sound_proc.type != \routine) {
			this.defineSynth;
			Server.local.sync;
			this.updateInstances { |inst|
				var bus = inst.getControlBus(this.name);
				inst.replaceAuxilSynth(this.name, synth_def, [out: bus, cutoff: inst.current_time])
			};
		}
	}


	dispose {
		if (this.sound_proc.type != \routine) {
			this.updateInstances { |inst|
				inst.freeAuxilSynth(this.name);
			}
		}
	}

	asString { ^"%: Env (%)".format(name, synth_def) }
}
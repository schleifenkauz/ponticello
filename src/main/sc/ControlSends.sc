ControlSends {
	classvar synth_def_created = false, synths;

	* initClass {
		synths = IdentityDictionary.new;
	}

	* ensure_synth_def {
		if (synth_def_created.not) {
			SynthDef(\control_send) {
				arg bus, rate, lag = 0, id;
				var sig = In.kr(bus);
				sig = Lag.kr(sig, lag);
				SendReply.kr(Impulse.kr(rate), '/control_send', sig, id);
			}.add;
			Server.local.sync;
		}
	}

	* start_control_send { |bus, id, rate, lag, target, addAction|
		var args;
		if (synths.includesKey(id)) {
			Error("Id % for control_send synth already taken.".format(id)).throw;
		};
		rate = rate ?? { Server.local.options.sampleRate / Server.local.options.blockSize };
		args = [bus: bus, rate: rate, lag: lag, id: id].postln;
		fork {
			this.ensure_synth_def;
			synths[id] = Synth(\control_send, args, target ? Server.local, addAction ? \addToTail);
		}
	}

	* stop_control_send { |id|
		var synth = synths.removeAt(id);
		if (synth.isNil) {
			postf("WARNING: \\control_send synth with id % not found\n", id);
		} {
			synth.free;
		}
	}

	* set_lag { |id, lag|
		synths[id].set(\lag, lag);
	}

	* set_rate { |id, rate|
		synths[id].set(\rate, rate);
	}
}
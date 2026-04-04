DefaultSynthDefs {
	classvar available_send_level_synth_defs;

	* initClass {
		available_send_level_synth_defs = Set[];
	}

	* addAll {
		SynthDef(\score_clock) {
			arg start = 0, rate = 1, out;
			var clock = start + Sweep.kr(rate: In.kr(rate));
			Out.kr(out, clock);
		}.add;
		SynthDef(\vst_plugin, {
			arg bus = 0;
			var dry = In.ar(bus, 2), snd, mix, dry_mix = \dry.kr(0), bypass = \bypass.kr(0);
			snd = VSTPlugin.ar(dry, 2, bypass: bypass);
			snd = snd + (dry * dry_mix);
			mix = Linen.kr(\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf) * (1 - bypass);
			XOut.ar(bus, mix, snd);
		}).add;
		SynthDef(\utility, {
			arg bus, volume, pan;
			var snd = In.ar(bus, 2);
			snd = snd * volume.dbamp * Linen.kr(\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf);
			Out.ar(bus, snd);
		}).add;
		SynthDef(\send, {
			arg in, out, amp;
			var snd = In.ar(in, 2);
			snd = snd * amp * Linen.kr(\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf);
			Out.ar(out, snd);
		}, metadata: (specs: (
			amp: [0, 1, \lin, 0.01, 1],
		))).add;
		this.setupSynthDefQueries;
	}

	* add_level_send_synth_def { |channels|
		var name = ("level_send_" ++ channels.asString).asSymbol;
		SynthDef(name) {
			arg bus, id;
			var sig = In.ar(bus, channels), rms, peak, msgValues;

			rms = Amplitude.kr(sig, 0.01, 0.3).ampdb;
			rms = rms.lag(\lag.kr(0.05));

			peak = PeakFollower.kr(sig, 0.999).ampdb;
			peak = peak.lag(\lag.kr(0.05));

			FreeSelf.kr(1 - \gate.kr(1));

			msgValues = if (channels == 1) { [rms, peak] } { rms ++ peak };
			SendReply.kr(Impulse.kr(\rate.kr(10)), '/bus_levels', msgValues, replyID: id)
		}.add;
		available_send_level_synth_defs.add(channels);
	}

	* create_level_send { |bus, reply_id, addAction, target, run=true, rate=10, lag=0.01|
		var def_name = ("level_send_" ++ bus.numChannels.asString).asSymbol;
		if (available_send_level_synth_defs.includes(bus.numChannels).not) {
			DefaultSynthDefs.add_level_send_synth_def(bus.numChannels);
			Server.local.sync;
		};
		^if (run) {
			^Synth(def_name, [bus: bus, id: reply_id, rate: rate, lag: lag], target, addAction);
		} {
			Synth.newPaused(def_name, [bus: bus, id: reply_id, rate: rate, lag: lag], target, addAction);
		}
	}

	* setupSynthDefQueries {
		Ponticello.respondResult('isSynthDef', { |name|
			SynthDescLib.global.synthDescs[name.asSymbol] != nil;
		});
		Ponticello.respondResult('removeSynthDef', { |name|
			SynthDescLib.global.synthDescs.removeAt(name);
		});
		Ponticello.respondResult('controls', { |name|
			SynthDescLib.global.synthDescs[name.asSymbol].controls.size;
		});
		Ponticello.respondResult('controlName', { |name, idx|
			SynthDescLib.global.synthDescs[name.asSymbol].controls[idx].name;
		});
		Ponticello.respondResult('controlDefault', { |name, idx|
			SynthDescLib.global.synthDescs[name.asSymbol].controls[idx].defaultValue;
		});
		Ponticello.respondResult('hasSpec', { |synthName, controlName|
			SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName] != nil;
		});
		Ponticello.respondResult('controlMinval', { |synthName, controlName|
			SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName][0];
		});
		Ponticello.respondResult('controlMaxval', { |synthName, controlName|
			SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName][1];
		});
		Ponticello.respondResult('controlWarp', { |synthName, controlName|
			SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName][2];
		});
		Ponticello.respondResult('controlStep', { |synthName, controlName|
			SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName][3];
		});
	}
}
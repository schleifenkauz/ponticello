LevelMeterFlow : AudioFlow {
	classvar synthdefs, lag = 0.01, rate = 10, instances;
	var bus, reply_id;

	* initClass {
		synthdefs = Set[];
		instances = List[];
	}

	* get_level_send_synthdef{ |channels|
		var name = ("level_send_" ++ channels.asString).asSymbol;
		if (synthdefs.includes(name).not) {
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
			Server.local.sync;
			synthdefs.add(channels);
		};
		^name
	}

	* create_level_send { |bus, reply_id, target, addAction|
		var synthdef = LevelMeterFlow.get_level_send_synthdef(bus.numChannels);
		^Synth(synthdef, [bus: bus, id: reply_id, rate: rate, lag: lag], target, addAction);
	}

	* new { |name, bus, reply_id| ^super.newCopyArgs(name, bus, reply_id); }

	createNode { |target, addAction|
		var def_name = LevelMeterFlow.get_level_send_synthdef(bus.numChannels);
		^this.prCreateSynth(def_name, [bus: bus, id: reply_id, rate: rate, lag: lag], target, addAction);
	}
}
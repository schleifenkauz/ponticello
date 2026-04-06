MixerFlow : AudioFlow {
	classvar synthdefs;
	var dest, <sources, <volumes, <pans, <filter, <masterVolume, <monoMix;

	* initClass {
		synthdefs = Set[];
	}

	* get_mixer_synth_def { |buses, channels, filter|
		var name = "mixer_%x%%".format(buses, channels, if (filter) { "_f" } { "" }).asSymbol;
		if (synthdefs.includes(name).not) {
			SynthDef(name) {
				var sources = NamedControl.kr(\sources, 0 ! buses, lags: 0.02, fixedLag: true);
				var volumes = NamedControl.kr(\volumes, 0 ! buses, lags: 0.02, fixedLag: true);
				var filters = NamedControl.kr(\filters, 0 ! buses, lags: 0.02, fixedLag: true);
				var snd, master_volume, dest;
				sources = In.ar(sources, channels) * volumes;
				if (channels == 2) {
					var pans = NamedControl.kr(\pans, 0 ! buses, lags: 0.02, fixedLag: true);
					if (buses == 1) {
						sources = Balance2.ar(sources[0], sources[1], pans);
					} {
						buses.do { |i|
							sources[i] = Balance2.ar(sources[i][0], sources[i][1], pans[i]);
						};
					}
				};
				if (filter) {
					var filters = NamedControl.kr(\filters, 0 ! buses, lags: 0.02, fixedLag: true);
					buses.do { |i|
						var dry, cutoff, lpf, hpf, filtered;
						dry = sources[i];
						lpf = BLowPass4.ar(dry, filters[i].abs.linexp(0, 1, 20000, 60), 0.5);
						hpf = BHiPass4.ar(dry, filters[i].abs.linexp(0, 1, 20, 12000), 0.5);
						filtered = SelectX.ar(filters[i].linlin(-1, 1, 0, 1) ! 2, [lpf, hpf]);
						sources[i] = XFade2.ar(dry, filtered, filters[i].abs);
					}
				};
				dest = \dest.kr(0);
				snd = In.ar(dest, channels);
				if (buses == 1) { snd = snd + sources};
				if (buses > 1) { snd = snd + sources.sum };
				snd = snd * \master_volume.kr(0, lag: 0.02, fixedLag: true);
				snd = snd * Linen.kr(\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf);
				if (channels == 2) {
					var mono_mix = \mono_mix.kr(0);
					snd = (snd * (1 - mono_mix)) + (snd.sum / 2 ! 2 * mono_mix);
				};
				ReplaceOut.ar(dest, snd);
			}.add;
			synthdefs.add(name);
			Server.local.sync;
		};
		^name
	}

	* new { |name, dest, sources, volumes, pans, filter, master_volume, mono_mix|
		^super.newCopyArgs(
			name, dest, sources, volumes, pans,
			filter, master_volume, mono_mix
		);
	}

	createNode { |target, addAction|
		var synthdef = MixerFlow.get_mixer_synth_def(sources.size, dest.numChannels, filter);
		^this.prCreateSynth(synthdef, [
			sources: sources, volumes: volumes,
			dest: dest, master_volume: masterVolume, mono_mix: monoMix
		], target, addAction);
	}

	dest_ { |bus|
		dest = bus;
		node.set(\dest, bus);
	}

	setSources { |buses, vol, pan|
		sources = buses;
		volumes = vol;
		pans = pan;
		this.recreate;
	}

	volumes_ { |values|
		volumes = values;
		node.setn(\volumes, values);
	}

	pans_ { |values|
		pans = values;
		node.setn(\pans, pans);
	}

	setSource { |idx, bus|
		sources[idx] = bus;
		node.setn(\sources, sources);
	}

	setVolume { |idx, volume|
		volumes[idx] = volume;
		node.setn(\volumes, volumes);
	}

	masterVolume_ { |vol|
		masterVolume = vol;
		node.set(\master_volume, vol);
	}

	monoMix_ { |mono|
		monoMix = mono;
		node.set(\mono_mix, mono);
	}

	filter_ { |active|
		filter = active;
		this.recreate;
	}
}
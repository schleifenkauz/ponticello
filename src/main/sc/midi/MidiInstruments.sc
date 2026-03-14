SoundProcessMidiInstrument {
	var procName, instancesByNote, <>enabled;

	* create { |name, instr, controls, enabled=true|
		SoundProcess.create(name, instr, controls);
		^super.newCopyArgs(name, Dictionary.new, enabled)
	}

	noteOn { |chan, num, val, track, src|
		if (enabled) {
            var proc = SoundProcess.get(procName);
            var inst = proc.createInstance(nil, 0, (pitch: num, velocity: val));
            inst.midiTrack = track;
            inst.onDispose { instancesByNote[num].remove(inst) };
            inst.start((addAction: \addToTail, target: track.group), src.latency, src.player_id);
            instancesByNote[num] = instancesByNote[num].add(inst);
		}
		^true
	}

	noteOff { |chan, num, val, track, src|
		instancesByNote[num].copy.do(_.release);
		^true
	}

	control { |chan, num, val, track, src|
		^true
	}


	allNotesOff {
		instancesByNote.keysValuesDo { |num, instances|
			instances.copy.do(_.release);
		};
	}

	activate {}

	dispose {}
}

VSTMidiInstrument {
	var vst_func, <>enabled, vst;

	* new { |vst, enabled=true|
		^super.newCopyArgs(vst, enabled);
	}

	prResolve {
		if (vst.isNil) {
			vst = vst_func.value();
		}
	}

	noteOn { |chan, num, val|
		if (enabled) {
			this.prResolve;
			vst.midi.noteOn(chan, num, val)
		}
		^true
	}

	noteOff { |chan, num, val|
		if (vst.notNil) {
			vst.midi.noteOff(chan, num, val)
		}
		^true
	}

	control { |chan, num, val|
		if (enabled) {
			this.prResolve;
			vst.midi.control(chan, num, val);
		}
		^true
	}

	allNotesOff { vst.midi.allNotesOff }

	activate {}

	dispose {}
}
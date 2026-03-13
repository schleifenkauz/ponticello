SoundProcessMidiInstrument {
	var procName, instancesByNote, notesInPedal, <>enabled;

	* new { |procName, enabled=true|
		^super.newCopyArgs(procName, Dictionary.new, [], enabled)
	}

	noteOn { |chan, num, val, track, src|
		if (enabled) {
		var proc = SoundProcess.get(procName);
		var inst = proc.createInstance(nil, 0, (pitch: num, velocity: val));
		inst.setupMidi(track, (latency: src.latency, player_id: src.player_id, instr: this));
		inst.onDispose { instancesByNote[num].remove(inst) };
		inst.start((addAction: \addToTail, target: track.group), src.latency, src.player_id);
		instancesByNote[num] = instancesByNote[num].add(inst);
		}
	}

	noteOff { |chan, num, velocity, track, src|
		postf("NoteOff %, src = %, pedal: %\n", num, src, track.pedalState);
		if ((track.pedalState > 0).not) {
			instancesByNote[num].postln;
			instancesByNote[num].copy.do(_.release);
		} {
			notesInPedal = notesInPedal.add(num);
		}
	}

	pedalUp { |track|
		notesInPedal.do { |num|
			this.noteOff(track, 0, num, 0);
		}
	}

	allNotesOff {
		instancesByNote.keysValuesDo { |num, instances|
			instances.copy.do(_.release);
		};
	}

	control { }
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

	noteOn { |chan, num, val, track, src|
		if (enabled) {
			this.prResolve;
			vst.midi.noteOn(chan, num, val)
		}
	}

	noteOff { |chan, num, val, track, src|
		if (vst.notNil) {
			vst.midi.noteOff(chan, num, val)
		}
	}

	control { |chan, num, val|
		if (enabled) {
			this.prResolve;
			vst.midi.control(chan, num, val);
		}
	}

	allNotesOff { vst.midi.allNotesOff }
}
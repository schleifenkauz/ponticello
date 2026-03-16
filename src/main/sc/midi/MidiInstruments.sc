SoundProcessMidiInstrument {
	var procName, instancesByNote, <>enabled;

	* create { |name, instr, controls, enabled=true|
		SoundProcess.create(name, instr, controls);
		^super.newCopyArgs(name, Dictionary.new, enabled)
	}

	noteOn { |num, val, chan, track, src|
		if (enabled) {
            var proc = SoundProcess.get(procName);
			var inst = proc.createInstance(nil, 0, [\pitch -> num, \velocity -> val]);
            inst.midiTrack = track;
            inst.onDispose { instancesByNote[num].remove(inst) };
            inst.start((addAction: \addToTail, target: track.group), src.latency, src.player_id);
            instancesByNote[num] = instancesByNote[num].add(inst);
		}
		^true
	}

	noteOff { |num, val, chan, track, src|
		instancesByNote[num].copy.do { |inst| inst.release(src.latency) };
		^true
	}

	control { |num, val, chan, track, src|
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
	var vst, <>enabled;

	* new { |vst, enabled=true|
		^super.newCopyArgs(vst, enabled);
	}

	noteOn { |num, val, chan, track, src|
		if (enabled) {
			var latency = src.latency ? 0;
			SystemClock.sched(latency) {
				vst.midi.noteOn(chan, num, val)
			}
		}
		^true
	}

	noteOff { |num, val, chan, track, src|
		if (vst.notNil) {
			SystemClock.sched(src.latency ? 0) {
				vst.midi.noteOff(chan, num, val);
			}
		}
		^true
	}

	control { |num, val, chan, track, src|
		if (enabled) {
			SystemClock.sched(src.latency ? 0) {
				vst.midi.control(chan, num, val);
			}
		}
		^true
	}

	allNotesOff { vst.midi.allNotesOff(0) }

	activate {}

	dispose {}
}
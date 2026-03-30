SoundProcessMidiInstrument : MidiInstrument {
	var procName, activeNotes, <>enabled;

	* create { |name, instr, controls, enabled=true|
		SoundProcess.create(name, instr, controls);
		^super.newCopyArgs(name, Dictionary.new, enabled)
	}

	noteOn { |num, val, src|
		if (enabled) {
            var proc = SoundProcess.get(procName);
			var midi_controls = [\pitch -> num, \velocity -> val];
			var inst = proc.createInstance(pos: nil, cutoff: 0, extra_controls: src.controls ++ midi_controls);
			var note = (src: src, instance: inst);
			var placement = (addAction: \addToTail, target: src.track.group);
			activeNotes[num] = activeNotes[num].add(note);
			inst.onDispose {
				activeNotes[num].remove(note)
			};
            inst.start(placement, src.server_latency, src.player_id, midiTrack: src.track);
		}
		^true
	}

	noteOff { |num, val, src|
		activeNotes[num].copy.do { |note|
			if (note.src.player_id == src.player_id) {
				note.instance.release(src.server_latency)
			}
		};
		^true
	}

	allNotesOff { |player_id|
		activeNotes.keysValuesDo { |num, instances|
			instances.copy.do { |note|
				if ((player_id == nil) || (note.src.player_id == player_id)) {
					note.instance.release;
				}
			}
		};
	}
}
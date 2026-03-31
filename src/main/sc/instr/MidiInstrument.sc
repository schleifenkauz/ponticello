MIDIInstrument {
	type { ^\midi }

	isAutoRelease { ^false }

	getDefaultValue { |param| ^nil }

	create {| inst |
		var velocity = inst.get(\velocity) ? 64;
		var midinote = inst.get(\pitch) ? 60;
		^MIDINote(midinote, velocity, inst);
	}

	asString { ^"MIDI instrument" }
}

MIDINote {
	var midinote, velocity, instance, src, playing = false;
    classvar <reserved_names;

	*initClass {
		reserved_names = Set['velocity', 'channel', 'pitch'];
	}

	* new {| midinote, velocity, inst |
		var src = inst.midiSource;
		^super.newCopyArgs(midinote, velocity, inst, src)
	}

	run { | active |
		if (playing != active) {
			playing = active;
			if (active) {
				instance.midi_track.noteOn(midinote, velocity, src)
			} {
				instance.midi_track.noteOff(midinote, 0, src);
			}
		}
	}

	set {| parameter, value |
        if (MIDINote.reserved_names.includes(parameter.not)) {
            instance.midi_track.instruments.do { |instr|
                if (instr.isKindOf(VSTMidiInstrument)) {
                    instr.vst.set(parameter, value);
                }
            }
        }
	}

	map {| parameter, bus |
		if (MIDINote.reserved_names.includes(parameter.not)) {
            instance.midi_track.instruments.do { |instr|
                if (instr.isKindOf(VSTMidiInstrument)) {
                    instr.vst.map(parameter, bus);
                }
            }
        }
	}

    release { |latency|
		src.server_latency = latency;
        instance.midi_track.noteOff(midinote, 0, src);
    }
}


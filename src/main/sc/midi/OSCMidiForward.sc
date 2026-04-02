OSCMidiForward : MidiInstrument {
	var track, inst;

	attachTo { |device_name|
		if (track.isNil) {
			inst = super.new;
			track = MidiTrack.new(nil, device_name, [inst]);
		} {
			track.sourceDevice = device_name;
		}
		^true;
	}

	noteOn { |num, val, src|
		Ponticello.sendMsg('/forward_note_on', num, val);
	}

	noteOff { |num, val, src|
		Ponticello.sendMsg('/forward_note_off', num, val);
	}

	control { |num, val, src|
		Ponticello.sendMsg('/forward_cc', num, val);
	}
}
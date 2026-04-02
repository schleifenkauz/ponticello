OSCMidiForward : MidiInstrument {
	var track;

	attachTo { |device_name|
		if (track.isNil) {
			track = MidiTrack.new(nil, device_name, [this]);
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
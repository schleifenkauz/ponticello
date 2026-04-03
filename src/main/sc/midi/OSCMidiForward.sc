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
		Ponticello.sendMsg('/forward_note_on', track.sourceDevice, num, val);
	}

	noteOff { |num, val, src|
		Ponticello.sendMsg('/forward_note_off', track.sourceDevice, num, val);
	}

	control { |num, val, src|
		Ponticello.sendMsg('/forward_cc', track.sourceDevice, num, val);
	}
}
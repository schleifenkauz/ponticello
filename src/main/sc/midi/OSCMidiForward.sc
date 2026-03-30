OSCMidiForward : MidiInstrument {
	classvar track, inst;

	* attachTo { |device_name|
		if (track.isNil) {
			inst = super.new;
			track = MidiTrack.new(nil, device_name, [inst]);
		} {
			track.sourceDevice = device_name;
		}
		^true;
	}

	control { |num, val, src|
		Ponticello.sendMsg('/forward_cc', num, val);
	}
}
OSCMidiForward {
	classvar device_uid, func;

	* attachTo { |device_name|
		if (MIDIClient.initialized.not) {
			MIDIClient.init(inports: 2, outports: 1, verbose: false);
		};
		if (func.isNil) {
			func = MIDIFunc.cc { |val, num, chan, uid|
				if (uid == device_uid) {
					Ponticello.sendMsg('/forward_cc', num, val);
				};
			}.permanent_(true);
		};
		if (device_uid.notNil) {
			MIDIIn.disconnect(1, device_uid);
		};
		^if (device_uid.notNil) {
			try {
				MIDIIn.connect(1, device_uid);
				true
			} { |exc|
				exc.reportError;
				false
			}
		} {	false}
	}
}
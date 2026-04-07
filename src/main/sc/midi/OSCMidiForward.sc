OSCMidiForward : MidiInstrument {
	var name, track;

	* new { |name| ^super.newCopyArgs(name) }

	attachTo { |device_name|
		if (track.isNil) {
			track = MidiTrack.new(name, device_name, [this]).active_(true);
		} {
			track.sourceDevice = device_name;
		}
		^true;
	}

	noteOn { |num, val, src|
		Ponticello.sendMsg('/forward_note_on', name, num, val);
	}

	noteOff { |num, val, src|
		Ponticello.sendMsg('/forward_note_off', name, num, val);
	}

	control { |num, val, src|
		Ponticello.sendMsg('/forward_cc', name, num, val);
	}
}

OSCHook : OSCdef {
	enable {
		super.enable;
		if (this.key.notNil) {
			Ponticello.sendMsg('/osc_hook_enabled', this.key);
		}
	}

	disable {
		super.disable;
		if (this.key.notNil) {
			Ponticello.sendMsg('/osc_hook_disabled', this.key);
		}
	}
}
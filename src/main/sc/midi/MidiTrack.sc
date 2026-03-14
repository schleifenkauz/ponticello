MidiTrack {
	var <sourceDevice, <>instruments, <activeNotes, <controlValues, <group, notesInPedal, connected=false;
	classvar initialized=false, tracksBySource, noteOn, noteOff, cc;

	* init {
		if (initialized.not) {
			var defaultSrc = (latency: 0, player_id: 0);
			MIDIClient.init(1, 1, verbose: false);
			tracksBySource = Dictionary.new;
			noteOn = MIDIFunc.noteOn { |val, num, chan, src|
				MidiTrack.dispatchEvent(src) { |track| track.noteOn(val, num, chan, defaultSrc) }
			}.permanent_(true);
			noteOff = MIDIFunc.noteOff { |val, num, chan, src|
				MidiTrack.dispatchEvent(src) { |track| track.noteOff(val, num, chan, defaultSrc) }
			}.permanent_(true);
			cc = MIDIFunc.cc { |val, num, chan, src|
				MidiTrack.dispatchEvent(src) { |track| track.control(val, num, chan, defaultSrc) }
			}.permanent_(true);
			initialized = true;
		}
	}

	* sourceDevicesString {
		var str = "";
		this.init;
		MIDIClient.externalSources.do { |src| str = str ++ "|" ++ src.device };
		^str
	}

	* outputDevicesString {
		var str = "";
		this.init;
		MIDIClient.externalDestinations.do { |src| str = str ++ "|" ++ src.device };
		^str
	}

	* getDeviceUID { |name|
		^MIDIClient.externalSources.detect { |p| p.device == name } !? (_.uid)
	}

	* dispatchEvent { |uid, fn|
		tracksBySource[uid].do(fn);
	}

	* connectSource { |deviceName, track|
		var uid;
		MidiTrack.init;
		uid = MidiTrack.getDeviceUID(deviceName);
		^if (uid.isNil) {
			postf("Warning: MIDI device % not found\n", deviceName);
			false
		} {
			tracksBySource[uid] = tracksBySource[uid].add(track);
			if (tracksBySource[uid].size == 1) {
				try {
					MIDIIn.connect(0, uid);
					true;
				} { |error|
					error.reportError;
					false;
				}
			} { true }
		}
	}

	* disconnectSource { |deviceName, track|
		var uid;
		MidiTrack.init;
		uid = MidiTrack.getDeviceUID(deviceName);
		if (uid.isNil) {
			postf("Warning: MIDI device % not found\n", deviceName);
		} {
			tracksBySource[uid].remove(track);
			if (tracksBySource[uid].size == 0) {
				try {
					MIDIIn.disconnect(0, uid)
				} { |error| error.reportError }
			}
		}
	}

	* new { |source, instrs|
		var track = super.newCopyArgs(source, instrs, Dictionary.new, Dictionary.new);
		track.prConnect;
		^track.activate;
	}

	* freeAll {
		tracksBySource.keysValuesDo { |src, tracks|
			tracks.copy.do(_.release);
		}
	}

	addToServer { |placement|
		group = MidiTrackGroup.new(placement, this);
		^group
	}

	prConnect {
		if (sourceDevice.notNil) {
			connected = MidiTrack.connectSource(sourceDevice, this);
		};
	}

	prDisconnect {
		if (sourceDevice.notNil && connected == true) {
			MidiTrack.disconnectSource(sourceDevice, this);
		};
		connected = false;
	}

	activate {
		instruments.do{ |instr| instr.activate(this) }
	}

	insertInstrument { |idx, instrument|
		if (instrument.notNil) {
			instruments.insert(idx, instrument);
			instrument.activate(this);
		} {
			Exception("")
		}
	}

	removeInstrument { |idx|
		var instrument = instruments.removeAt(idx);
		instrument.dispose;
	}

	sourceDevice_ { |deviceName|
		if (deviceName != sourceDevice) {
			this.prDisconnect;
			sourceDevice = deviceName;
			this.prConnect;
		}
	}

	release {
		if (group != nil) {
			instruments.do(_.allNotesOff);
			group.free;
			group = nil;
			MidiTrack.disconnectSource(sourceDevice, this);
		} {
			postf("Already released track with source %\n", sourceDevice);
		}
	}

	run { |v|
		if (v) { this.prConnect } { this.prDisconnect }
	}

	perform { |src, fn|
		fork {
			var idx = (instruments.indexOf(src.instr) ? -1) + 1, continue = true;
			postf("Starting at index % (src instr: %)\n", idx, src.instr);
			while { (continue == true) && (idx < instruments.size) } {
				var instr = instruments[idx];
				continue = fn.value(instr) ? true;
				if (continue.isKindOf(Function)) {
					fn = continue;
					continue = true;
				};
				idx = idx + 1;
			}
		}
	}

	noteOn { |val, num, chan=0, src|
		postf("Note On: %, %\n", num, val);
		activeNotes[num] = val;
		this.perform(src) { |instr|
			instr.noteOn(chan, num, val, this, src)
		}
	}

	noteOff { |val, num, chan=0, src|
		if (this.isPedalDown.not) {
			activeNotes.removeAt(num);
			this.perform(src) { |instr| instr.noteOff(chan, num, val, this, src) }
		} {
			notesInPedal = notesInPedal.add(num);
		}
	}

	control { |val, num, chan=0, src|
		controlValues[num] = val;
		if (num == 64 && val == 0) {
			notesInPedal.do { |num|
				this.noteOff(0, num, 0, src);
			}
		};
		this.perform(src) { |instr| instr.control(chan, num, val, this, src) }
	}

	isPedalDown { ^(controlValues[64] ? 0) > 0 }
}

MidiTrackGroup : Group {
	var <>track;

	* new { |placement, track|
		var group = super.new(placement.target, placement.addAction);
		group.track = track;
		^group;
	}

	run { |active|
		track.run(active);
	}
}
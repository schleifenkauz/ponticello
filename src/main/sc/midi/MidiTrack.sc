MidiTrack {
	var <>name, <group, <sourceDevice, <>instruments, <enabled, <activeNotes, <controlValues, notesInPedal, connected=false, <recorder;
	classvar initialized=false, tracksBySource, noteOn, noteOff, cc;

	* init {
		if (initialized.not) {
			if (MIDIClient.initialized.not) {
				MIDIClient.init(inports: 1, outports: 1, verbose: false);
			};
			tracksBySource = Dictionary.new;
			noteOn = MIDIFunc.noteOn { |val, num, chan, uid|
			    var src = (player_id: -1, server_latency: 0, chan: chan);
				MidiTrack.dispatchEvent(uid) { |track| track.noteOn(num, val, src) }
			}.permanent_(true);
			noteOff = MIDIFunc.noteOff { |val, num, chan, uid|
                var src = (player_id: -1, server_latency: 0, chan: chan);
				MidiTrack.dispatchEvent(uid) { |track| track.noteOff(num, val, src) }
			}.permanent_(true);
			cc = MIDIFunc.cc { |val, num, chan, uid|
                var src = (player_id: -1, server_latency: 0, chan: chan);
				MidiTrack.dispatchEvent(uid) { |track| track.control(num, val, src) }
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

	* new { |name, group, source, instrs, enabled=true|
		var track = super.newCopyArgs(
			name, group, source, instrs, enabled,
			Dictionary.new, Dictionary.new
		);
		if (enabled) {
			track.prConnect;
			^track.activate;
		}
		^track;
	}

	* freeAll {
		if (initialized) {
			tracksBySource.keysValuesDo { |src, tracks|
				tracks.copy.do(_.release);
			}
		}
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
		recorder = MidiRecorder.new;
		instruments.do{ |instr| instr.activate(this) }
	}

	insertInstrument { |idx, instrument|
		if (instrument.notNil) {
			instruments.insert(idx, instrument);
			instrument.activate(this);
		} {
			Exception("Attempt to insert nil").throw;
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
			this.allNotesOff;
			group.free;
			group = nil;
			MidiTrack.disconnectSource(sourceDevice, this);
		} {
			postf("Already released track with source %\n", sourceDevice);
		}
	}

	run { |v|
		enabled = v;
		if (v) { this.prConnect } { this.prDisconnect };
		Ponticello.sendMsg('/set_track_active', name, v);
	}

	perform { |src, fn|
		if (enabled) {
			fork {
				var idx = (instruments.indexOf(src !? (_.instr)) ? -1) + 1, continue = true;
				//postf("Starting at index % (src instr: %)\n", idx, src.instr);
				while { (continue == true) && (idx < instruments.size) } {
					var instr = instruments[idx];
					//postf("Performing on %\n", instr);
					continue = fn.value(instr) ? true;
					if (continue.isKindOf(Function)) {
						fn = continue;
						continue = true;
					};
					idx = idx + 1;
				};
				if ((continue == true) && recorder.notNil) {
					fn.value(recorder);
				}
			}
		}
	}

	noteOn { |num, val, src|
		if (enabled) {
			src.track = this;
			//postf("Note On %, % (src: %)\n", num, val, src);
			activeNotes[num] = activeNotes[num].add(src);
			notesInPedal.remove(num);
			this.perform(src) { |instr|
				instr.noteOn(num, val, src)
			}
		}
	}

	noteOff { |num, val, src, force=false|
		if (enabled || force) {
			src.track = this;
			//postf("Note Off %, % (src: %)\n", num, val, src);
			if (this.isPedalDown.not) {
				activeNotes[num].remove(src);
				this.perform(src) { |instr|
					instr.noteOff(num, val, src)
				};
				if (src.respondsTo(\dispose)) {
					src.dispose;
				}
			} {
				notesInPedal = notesInPedal.add(num);
			}
		}
	}

	control { |num, val, src|
		if (enabled) {
			src.track = this;
			controlValues[num] = val;
			if (num == 64 && val == 0) {
				notesInPedal.do { |num|
					this.noteOff(num, val, src);
				}
			};
			this.perform(src) { |instr|
				instr.control(num, val, src)
			};
		}
	}

	isPedalDown { ^(controlValues[64] ? 0) > 0 }

	activeNotesDo { |fn|
		activeNotes.copy.keysValuesDo { |num, notes|
			notes.do { |src|
				fn.value(num, src);
			}
		}
	}

	stopEffect { |eff|
		this.activeNotesDo { |num, src|
			if (src.instr == eff) {
				this.noteOff(num, 0, src);
			}
		}
	}

	allNotesOff { |player_id|
		if (player_id.isNil || (player_id == -1)) {
			instruments.do(_.allNotesOff);
		} {
			this.activeNotesDo { |num, src|
				if (src.player_id == player_id) {
					this.noteOff(num, 0, src, force:true);
				}
			}
		}
	}
}
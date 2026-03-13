MidiTrack {
	var <sourceDevice, <>instruments, <group, <pedalState;
	classvar tracksBySource, noteOn, noteOff, cc;

	* initClass {
		var defaultSrc = (latency: 0, player_id: 0);
		MIDIClient.init(1, 1, verbose: false);
		tracksBySource = Dictionary.new;
		ServerTree.add {
			noteOn = MIDIFunc.noteOn { |val, num, chan, src|
				MidiTrack.dispatchEvent(src) { |track| track.noteOn(val, num, chan, defaultSrc) }
			};
			noteOff = MIDIFunc.noteOff { |val, num, chan, src|
				MidiTrack.dispatchEvent(src) { |track| track.noteOff(val, num, chan, defaultSrc) }
			};
			cc = MIDIFunc.cc { |val, num, chan, src|
				MidiTrack.dispatchEvent(src) { |track| track.control(val, num, chan, defaultSrc) }
			}
		}
	}

	* dispatchEvent { |uid, fn|
		tracksBySource[uid].do(fn);
	}

	* getDeviceUID { |name|
		^MIDIClient.externalSources.detect { |p| p.name == name } !? (_.uid)
	}

	* connectSource { |deviceName, track|
		var uid = MidiTrack.getDeviceUID(deviceName);
		if (uid.isNil) {
			Exception("MIDI device % not found".format(deviceName)).throw;
		};
		tracksBySource[uid] = tracksBySource[uid].add(track);
		if (tracksBySource[uid].size == 1) {
			MIDIIn.connect(0, uid);
		}
	}

	* disconnectSource { |deviceName, track|
		var uid = MidiTrack.getDeviceUID(deviceName);
		if (uid.isNil) {
			Exception("MIDI device % not found".format(deviceName)).throw;
		};
		tracksBySource[uid].remove(track);
		if (tracksBySource[uid].size == 0) {
			MIDIIn.disconnect(0, uid);
		}
	}

	* new { |source, instrs, placement|
		var group = Group.new(placement.target, placement.addAction);
		var track = super.newCopyArgs(source, instrs, group, 0);
		if (source.notNil) {
			MidiTrack.connectSource(source, track);
		}
		^track
	}

	* freeAll {
		tracksBySource.keysValuesDo { |src, tracks|
			tracks.copy.do(_.release);
		}
	}

	sourceDevice_ { |deviceName|
		MidiTrack.disconnectSource(sourceDevice, this);
		sourceDevice = deviceName;
		MidiTrack.connectSource(deviceName, this);
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

	perform { |src, fn|
		instruments.drop((instruments.indexOf(src.instr) ? -1) + 1).do { |instr|
			fn.value(instr)
		};
	}

	noteOn { |val, num, chan=0, src|
		this.perform(src) { |instr|
			instr.noteOn(chan, num, val, this, src)
		}
	}

	noteOff { |val, num, chan=0, src|
		this.perform(src) { |instr| instr.noteOff(chan, num, val, this, src) }
	}

	control { |val, num, chan=0, src|
		if (num == 64) {
			pedalState = val;
			if (val == 0) {
				instruments.do { |instr| instr.pedalUp(this); }
			}
		};
		this.perform(src) { |instr| instr.control(chan, num, val, this, src) }
	}
}

SoundProcessInstrument {
	var proc, instancesByNote, notesInPedal;

	* new { |proc|
		^super.newCopyArgs(proc, Dictionary.new)
	}

	noteOn { |chan, num, val, track, src|
		fork {
			var inst = proc.createInstance(nil, 0, (pitch: num, velocity: val));
			inst.setupMidi(track, (latency: src.latency, player_id: src.player_id, instr: this));
			inst.onDispose { instancesByNote[num].remove(inst) };
			inst.start((addAction: \addToTail, target: track.group), src.latency, src.player_id);
			instancesByNote[num] = instancesByNote[num].add(inst);
		}
	}

	noteOff { |chan, num, velocity, track, src|
		postf("NoteOff %, src = %, pedal: %\n", num, src, track.pedalState);
		if ((track.pedalState > 0).not) {
			instancesByNote[num].postln;
			instancesByNote[num].copy.do(_.release);
		} {
			notesInPedal = notesInPedal.add(num);
		}
	}

	pedalUp { |track|
		notesInPedal.do { |num|
			this.noteOff(track, 0, num, 0);
		}
	}

	allNotesOff {
		instancesByNote.keysValuesDo { |num, instances|
			instances.copy.do(_.release);
		};
	}

	control { }
}

+ SoundProcessInstance {
	pitch { ^this.getControlValue('pitch') }
	velocity { ^this.getControlValue('velocity') }
}
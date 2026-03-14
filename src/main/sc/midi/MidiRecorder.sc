MidiRecorder {
	var events, recording=false, firstNoteTime, recordingId;

	* new {
		^super.newCopyArgs(List[]);
	}

	startRecording { |id|
		if (recording) {
			Exception("Already recording").throw;
		} {
			recordingId = id;
			recording = true
		}
	}

	finishRecording {
		if (recording.not) {
			Exception("Not recording").throw;
		};
		~ponticello_addr.sendMsg('/midi_recording_finished', recordingId, events.size, *events.flatten);
		recording = false;
		recordingId = nil;
		firstNoteTime = nil;
		events = List[];
	}

	noteOn { |num, val, chan, src|
		if (recording) {
			var t;
			if (firstNoteTime.notNil) { t = SystemClock.seconds - firstNoteTime } {
				firstNoteTime = SystemClock.seconds;
				t = 0.0;
			};
			events.add([t, 1, num, val, chan]);
		}
	}

	noteOff { |num, val, chan, src|
		if (recording && firstNoteTime.notNil) {
			var t = SystemClock.seconds - firstNoteTime;
			events.add([t, 0, num, val, chan]);
		}
	}
}
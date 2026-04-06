MidiRecorder {
	var events, recording=false, firstNoteTime, recordingId, pedalState = 0;

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
		if (events.isEmpty.not) {
			if (pedalState != 0) {
				events.add(events.last[0], 2, 64, 0, 0);
			};
			Ponticello.sendMsg('/midi_recording_finished', recordingId, events.size, *events.flatten);
		};
		recording = false;
		recordingId = nil;
		firstNoteTime = nil;
		pedalState = 0;
		events = List[];
	}

	noteOn { |num, val, src|
		if (recording) {
			var t = if (firstNoteTime.notNil) { SystemClock.seconds - firstNoteTime } {
				firstNoteTime = SystemClock.seconds;
				0.0;
			};
			events.add([t, 1, num, val, src.chan]);
		}
	}

	noteOff { |num, val, src|
		if (recording && firstNoteTime.notNil) {
			var t = SystemClock.seconds - firstNoteTime;
			events.add([t, 0, num, val, src.chan]);
		}
	}

	control { |num, val, src|
		if (recording) {
			var t = if (firstNoteTime.notNil) { SystemClock.seconds - firstNoteTime } { 0.0 };
			events.add([t, 2, num, val, src.chan]);
			if (num == 64) { pedalState = val };
		}
	}
}
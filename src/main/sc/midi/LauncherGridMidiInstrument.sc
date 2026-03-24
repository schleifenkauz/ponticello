LauncherGridMidiInstrument {
	var <items, <modes, name, <>enabled, on, track;

	* new { |items, modes, name, enabled=true|
		var on = List.fill(16, false);
		items = items ?? { List.fill(16) };
		modes = modes ?? { List.fill(16) };
		^super.newCopyArgs(items, modes, name, enabled, on)
	}

	setItems { |itemList, modeList|
		items = itemList; modes = modeList;
	}

	setItem { |idx, item| //TODO there seems to be an issue here
		if (items[idx].notNil && on[idx]) {
			items[idx].noteOff(idx, 0, modes[idx]);
		};
		items[idx.postln] = item.postln;
	}

	setMode { |idx, mode|
		modes[idx] = mode;
	}

	swapItems { |i, j|
		items.swap(i, j);
		modes.swap(i, j);
	}

	noteOn { |num, velocity, src|
		var mode;
		num = num - 36;
		mode = modes[num];
		postf("Note On %: %, %\n", num, items[num], mode);
		if (items[num].notNil) {
			src.track = track;
			items[num].noteOn(velocity, src, mode);
		};
		Ponticello.sendMsg('/grid_item_note_on', name, num);
		^true
	}

	noteOff { |num, velocity, src|
		var mode;
		num = num - 36;
		mode = modes[num];
		if (items[num].notNil) {
			src.track = track;
			items[num].noteOff(velocity, src, mode);
		};
		Ponticello.sendMsg('/grid_item_note_off', name, num);
		^true
	}

	control { ^true }

	allNotesOff { }

	activate { |trk|
		track = trk;
	}

	dispose {}
}

SoundProcessGridItem {
	var proc, inst;

	* new { |procName| ^super.newCopyArgs(SoundProcess.get(procName)) }

	noteOn { |velocity, src, mode|
		if (inst.isNil || (mode != \toggle)) {
			var midi_controls = [\velocity -> velocity];
			var extra_controls = src.controls ++ midi_controls;
			var placement = (addAction: \addToTail, target: src.track.group);
			inst = proc.createInstance(nil, 0, extra_controls);
			inst.onDispose { inst = nil };
			inst.start(placement, src.server_latency, src.player_id, midiTrack: src.track);
		} {
			inst.release;
		}
	}

	noteOff { |velocity, src, mode|
		if (mode == \gate) {
			inst.release;
		}
	}
}

AudioFlowGridItem {
	var flowId;

	* new { |flowId| ^super.newCopyArgs(flowId) }

	noteOn { |velocity, src, mode|
		switch (mode)
		{ \gate } { Ponticello.sendMsg('/activate_flow', flowId) }
		{ \toggle } { Ponticello.sendMsg('/toggle_flow', flowId) }
	}

	noteOff { |velocity, src, mode|
		if (mode == \gate) {
			Ponticello.sendMsg('/deactivate_flow', flowId)
		}
	}
}

ScriptGridItem {
	var scriptId;

	* new { |scriptId| ^super.newCopyArgs(scriptId) }

	noteOn { |velocity, src, mode|
		Ponticello.sendMsg('/run_script', scriptId)
	}

	noteOff { |velocity, src, mode|
	}
}

PlayerGridItem {
	var playerId;

	* new { |playerId| ^super.newCopyArgs(playerId) }

	noteOn { |velocity, src, mode|
		switch (mode)
		{ \gate } { Ponticello.sendMsg('/start_live_obj', playerId) }
		{ \toggle } { Ponticello.sendMsg('/toggle_live_obj', playerId) }
	}

	noteOff { |velocity, src, mode|
		switch (mode)
		{ \gate } { Ponticello.sendMsg('/pause_live_obj', playerId) }
	}
}

ToggleRecordingGridItem {
	var bufferId;

	* new { |bufferId| ^super.newCopyArgs(bufferId) }

	noteOn { |velocity, src, mode|
		Ponticello.sendMsg('/toggle_recording', bufferId)
	}

	noteOff { |velocity, src, mode| }
}

PlaybackActionItem {
	var type;

	* new { |type| ^super.newCopyArgs(type) }

	noteOn { |velocity, src, mode|
		switch (type)
		{ \play } {
			switch (mode)
			{ \gate } { Ponticello.sendMsg('/start_play') }
			{ \toggle } { Ponticello.sendMsg('/toggle_play') }
		}
		{ \stop } { Ponticello.sendMsg('/stop_playback') }
		{ \gotostart } { Ponticello.sendMsg('/go_to_start') }
	}

	noteOff { |velocity, src, mode|
		if ((type == \play) && (mode == \gate)) {
			Ponticello.sendMsg('/pause_play')
		}
	}
}
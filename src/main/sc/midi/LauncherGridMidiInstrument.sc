LauncherGridMidiInstrument {
	var <items, <modes, id, <>enabled, on;

	* new { |items, modes, id, enabled=true|
		var on = List.fill(16, false);
		items = items ?? { List.fill(16) };
		modes = items ?? { List.fill(16) };
		^super.newCopyArgs(items, modes, id, enabled, on)
	}

	setItems { |itemList, modeList|
		items = itemList; modes = modeList;
	}

	setItem { |idx, item, mode|
		if (items[idx].notNil && on[idx]) {
			items[idx].noteOff(idx, 0, modes[idx]);
		};
		items[idx] = item;
		modes[idx] = mode;
	}

	setMode { |idx, mode|
		modes[idx] = mode;
	}

	swapItems { |i, j|
		items.swap(i, j);
		modes.swap(i, j);
	}

	noteOn { |num, velocity, src|
		var mode = modes[num];
		if (items[num].notNil) {
			items[num].noteOn(num, velocity, src, mode);
		};
		Ponticello.sendMsg('/grid_item_note_on', id, num);
	}

	noteOff { |num, velocity, src|
		var mode = modes[num];
		if (items[num].notNil) {
			items[num].noteOff(num, velocity, src, mode);
		};
		Ponticello.sendMsg('/grid_item_note_off', id, num);
	}

	control { }

	allNotesOff { }

	activate {}

	dispose {}
}

SoundProcessGridItem {
	var proc, inst;

	* new { |procName| ^super.new(SoundProcess.get(procName)) }

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

	* new { |flowId| ^super.new(flowId) }

	noteOn { |velocity, src, mode|
		Ponticello.sendMsg('/activate_flow', flowId)
	}

	noteOff { |velocity, src, mode|
		if (mode == \gate) {
			Ponticello.sendMsg('/deactivate_flow', flowId)
		}
	}
}

ScriptGridItem {
	var scriptId;

	* new { |scriptId| ^super.new(scriptId) }

	noteOn { |velocity, src, mode|
		Ponticello.sendMsg('/run_script', scriptId)
	}

	noteOff { |velocity, src, mode|
	}
}

PlayerGridItem {
	var playerId;

	* new { |playerId| ^super.new(playerId) }

	noteOn { |velocity, src, mode|
		case (mode)
		{ \gate } { Ponticello.sendMsg('/start_live_obj', playerId) }
		{ \toggle } { Ponticello.sendMsg('/toggle_live_obj', playerId) }
	}

	noteOff { |velocity, src, mode|
		case (mode)
		{ \gate } { Ponticello.sendMsg('/pause_live_obj', playerId) }
	}
}

ToggleRecordingGridItem {
	var bufferId;

	* new { |bufferId| ^super.new(bufferId) }

	noteOn { |velocity, src, mode|
		Ponticello.sendMsg('/toggle_recording', bufferId)
	}

	noteOff { |velocity, src, mode| }
}
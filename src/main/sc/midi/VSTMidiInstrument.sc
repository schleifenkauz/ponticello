VSTMidiInstrument : MidiInstrument {
	var <vst, <>enabled;

	* new { |vst, enabled=true|
		^super.newCopyArgs(vst, enabled);
	}

	prApplyControls { |src|
		src.controls.do { |ctrl|
			var argument = ctrl.getSynthArgument(src);
			case
			{ vst.info.parameters.any { |p| p.name == ctrl.name }.not } {}
			{ argument.isKindOf(Number) } { vst.set(ctrl.name, argument) }
			{ argument.isKindOf(Symbol) && argument.asString[0] == 'a' } {
				var index = argument.asString.drop(1).asInteger;
				if (index != 0 || argument == 'a0') {
					vst.map(ctrl.name, Bus(\audio, index, 1, Server.local));
				}
			}
			{ argument.isKindOf(Symbol) && argument.asString[0] == 'c' } {
				var index = argument.asString.drop(1).asInteger;
				if (index != 0 || argument == 'c0') {
					vst.map(ctrl.name, Bus(\control, index, 1, Server.local));
				}
			}
		};
	}

	noteOn { |num, val, src|
		if (enabled) {
			SystemClock.sched(src.server_latency ? 0) {
				this.prApplyControls(src);
				vst.midi.noteOn(src.chan, num, val)
			}
		}
		^true
	}

	noteOff { |num, val, src|
		if (vst.notNil) {
			SystemClock.sched(src.server_latency ? 0) {
				vst.midi.noteOff(src.chan, num, val);
			}
		}
		^true
	}

	control { |num, val, src|
		if (enabled) {
			SystemClock.sched(src.server_latency ? 0) {
				vst.midi.control(src.chan, num, val);
			}
		}
		^true
	}

	allNotesOff { vst.midi.allNotesOff(0) }
}
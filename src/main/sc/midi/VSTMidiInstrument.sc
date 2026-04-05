VSTMidiInstrument : MidiInstrument {
	var <flow, <enabled;

	* new { |flow, enabled=true|
		^super.newCopyArgs(flow, enabled);
	}

	vst { ^flow.controller }

	prApplyControls { |src|
		src.controls.do { |ctrl|
			var argument = ctrl.getSynthArgument(src);
			case
			{ this.vst.info.parameters.any { |p| p.name == ctrl.name }.not } {}
			{ argument.isKindOf(Number) } { this.vst.set(ctrl.name, argument) }
			{ argument.isKindOf(Symbol) && argument.asString[0] == 'a' } {
				var index = argument.asString.drop(1).asInteger;
				if (index != 0 || argument == 'a0') {
					this.vst.map(ctrl.name, Bus(\audio, index, 1, Server.local));
				}
			}
			{ argument.isKindOf(Symbol) && argument.asString[0] == 'c' } {
				var index = argument.asString.drop(1).asInteger;
				if (index != 0 || argument == 'c0') {
					this.vst.map(ctrl.name, Bus(\control, index, 1, Server.local));
				}
			}
		};
	}

	activate { |track|
		if (enabled) {
			flow.active_(true, notify:false);
		};
		flow.create(target: track.group, addAction: \addToTail);
	}

	dispose {
		flow.release;
	}

	enabled_ { |enable|
		enabled = enable;
		flow.active_(enable, notify: false);
	}

	noteOn { |num, val, src|
		if (enabled) {
			SystemClock.sched(src.server_latency ? 0) {
				this.prApplyControls(src);
				this.vst.midi.noteOn(src.chan, num, val)
			}
		}
		^true
	}

	noteOff { |num, val, src|
		if (this.vst.notNil) {
			SystemClock.sched(src.server_latency ? 0) {
				this.vst.midi.noteOff(src.chan, num, val);
			}
		}
		^true
	}

	control { |num, val, src|
		if (enabled) {
			SystemClock.sched(src.server_latency ? 0) {
				this.vst.midi.control(src.chan, num, val);
			}
		}
		^true
	}

	allNotesOff { this.vst.midi.allNotesOff(0) }
}
GlobalVar {
	var name, <bus, <>scale, fader, poll, >fadeTime = 1, last_value, midi_func, min, max, step_size, synthCode;
	classvar fader_group, instances;

	* new { |name, value, scale = \lin|
		var inst = instances[name], bus;
		if (inst.notNil) {
			if (value.notNil) {
				inst.set(value)
			};
			^inst
		};
		bus = Bus.control(Server.local, 1);
		bus.set(value);
		inst = this.newCopyArgs(name, bus, scale);
		instances[name] = inst;
		^inst
	}

	range { |hi, lo, step|
		min = hi; max = lo; step_size = step;
	}

	get { ^bus.getSynchronous }

	clip { |value|
		if (min.notNil) { value = value.max(min) };
		if (max.notNil) { value = value.min(max) };
		^value;
	}

	poll { |rate = 1|
		poll.free;
		if (rate != 0) {
			poll = { bus.kr.poll(rate, name) }.play(fader_group, addAction: \addToTail);
		} {
			poll = nil;
		}
	}

	set { |value|
		fader.free;
		bus.set(this.clip(value));
	}

	asBus { ^bus }

	kr { ^bus.kr }

	asMap { ^bus.asMap }

	free {
		fader.free;
		bus.free;
		midi_func.free;
	}

	* clearAll {
		instances.do(_.free);
		instances = IdentityDictionary[];
	}

	* dumpState {
		postf("GLOBAL VARIABLES: [");
		instances.keysValuesDo { |name, v|
			postf("%: %\,", name, v.get);
		};
		postf("]");
	}

	* faderGroup {
		if (fader_group.isNil) {
			fader_group = Group.new(Server.local.defaultGroup, \addBefore);
			fader_group.onFree { fader_group = nil };
		}
		^fader_group
	}

	* initClass {
		instances = IdentityDictionary[];
		SynthDef(\fade_value) { |bus, from, to, dur|
			var value = Line.kr(from, to, dur, doneAction: Done.freeSelf);
			Out.kr(bus, value);
		}.add;
		SynthDef(\xfade_value) { |bus, from, to, dur|
			var value = XLine.kr(from, to, dur, doneAction: Done.freeSelf);
			Out.kr(bus, value);
		}.add;
	}

	bindMidi { |cc_num, device, sensitivity = 3|
		if (step_size.isNil) { Error("Cannot bind MIDI knob without configured step-size").throw };
		midi_func.free;
		midi_func = MIDIFunc.cc({ |val, num, chan, uid|
			//postf("%: %, (%)\n", num, val, uid);
			if ((num == cc_num) && (uid == device.uid || device.isNil)) {
				var factor = (if (val >= 64) { (128 - val).neg } { val }).pow(sensitivity);
				var current = this.get;
				var v = ((current / step_size).round + factor.round) * step_size;
				v = this.clip(v);
				postf("%: %\n", name, v);
				this.set(v);
			}
		}).fix;
	}

	unbindMidi {
		midi_func.free;
	}

	applyEnv { |env|
		var synth = { this.clip(env.kr(Done.freeSelf)).poll(1, name) }.play(fader_group, bus);
		this.prSetFaderSynth(synth);
	}

	prSetFaderSynth { |synth, on_free|
		fader.free;
		fader = synth;
		synth.onFree {
			on_free.value;
			if (fader == synth) { fader = nil }
		};
	}

	fadeTime { |update|
		^fadeTime = update ? fadeTime;
	}

	bind { |func, fade_time|
		var synth, code;
		last_value = this.get;
		synth = {
			var sig = func.value;
			var fade = Line.kr(0, 1, this.fadeTime(fade_time));
			fade.linscale(scale, 0, 1, last_value, sig);
		}.play(fader_group, bus);
		code = func.asCompileString;
		synthCode = code;
		this.prSetFaderSynth(synth) {
			if (synthCode == code) {
				synthCode = nil;
			}
		}
	}

	unbind { |fade_time|
		fader.free;
		fader = nil;
		this.fade(last_value, fade_time);
	}

	* prFaderSynthDef { |scale|
		^scale.switch
		{ \lin } { \fade_value }
		{ \exp } { \xfade_value }
		{ Error("Invalid scale type %".format(scale)).throw }
	}

	fade { |value, fade_time, custom_scale|
		fader.free;
		value = this.clip(value);
		fade_time = this.fadeTime(fade_time);
		if ((fade_time <= 0) || ((value - this.get).abs < (step_size ? 0.001))) {
			bus.set(value);
		} {
			var start = bus.getSynchronous;
			var synthdef = GlobalVar.prFaderSynthDef(custom_scale ? scale);
			var synth = Synth(synthdef, [bus: bus, from: start, to: value, dur: fade_time], fader_group);
			this.prSetFaderSynth(synth) {
				postf("% fade finished: % -> %\n", name, start, value);
			}
		}
	}

	xfade { |value, fade_time| ^this.fade(value, fade_time) }

	state { ^synthCode ?? { this.get.round(step_size ? 0.001) } }

	asString { ^"%: %".format(name, this.state) }

	* state {
		var state = ();
		instances.keysValuesDo { |name, v|
			state[name] = v.state;
		};
		^state
	}

	* recallState { |state, fade_time|
		state.keysValuesDo { |name, st|
			var v = GlobalVar(name);
			case
			{ st.isKindOf(Number) } { v.fade(st, fade_time) }
			{ st.isKindOf(String) } { v.bind(st.compile.value, fade_time) }
			{ st.isKindOf(Function) } { v.bind(st, fade_time) }
			{ postf("WARNING: Invalid variable state %\n", st) }
		}
	}

	* saveState { |filename|
		var json_str = JSONlib.convertToJSON(this.state);
		var file = File(filename, "w");
		file.write(json_str);
		file.close;
	}

	* loadState { |filename|
		var file = File(filename, "r");
		var json_str = file.readAllString;
		var state = JSONlib.convertToSC(json_str);
		GlobalVar.recallState(state, fade_time: 0);
	}
}

+ Object{
	toClipboard {
		var p = Pipe("wl-copy", "w");
		p.write(this.asString);
		p.close;
	}
}

+ Symbol {
	asMap { |initial_value| ^GlobalVar(this, initial_value).asMap }
}

+ Dictionary {
	fade { |fade_time, wait=false|
		GlobalVar.recallState(this, fade_time);
		if (wait) {
			fade_time.wait;
		}
	}
}
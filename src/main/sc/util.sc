+ DUGen {
	kr { |dur, reset=0, doneAction=0| ^Duty.kr(dur, reset, this, doneAction) }

	ar { |dur, reset=0, doneAction=0| ^Duty.ar(dur, reset, this, doneAction) }

	krTrig { |trig, reset=0, doneAction=0| ^Demand.kr(trig, reset, this) }

	arTrig { |trig, reset=0, doneAction=0| ^Demand.ar(trig, reset, this) }
}

+ UGen {
	avg { |time, max_time|
		var num_samp = ControlRate.ir * time;
		var max_samp = ControlRate.ir * (max_time ? time);
		^MovingAverage.kr(this, num_samp, max_samp)
	}

	delay { |time, max_time|
		^switch(this.rate)
		{ \control } { DelayL.kr(this, max_time ? time, time) }
		{ \audio } { DelayL.ar(this, max_time ? time, time) }
		{ Error("% has invalid UGen rate % for .delay".format(this, this.rate)).throw }
	}

	linscale { |scale=\lin, inMin=0, inMax=1, outMin=0, outMax=1, clip=\minmax |
		^scale.switch
		{ \lin } { this.linlin(inMin, inMax, outMin, outMax, clip) }
		{ \exp } { this.linexp(inMin, inMax, outMin, outMax, clip) }
		{ Error("Invalid scale %".format(scale)).throw }
	}
}

+ Synth {
	mapEnv { |param, env|
		var bus = Bus.control;
		{ env.kr(Done.freeSelf) }.play(this, bus, addAction: \addBefore).onFree { bus.free };
		this.map(param, bus);
	}
}

+ Env {
	asMap { |target|
		var bus = Bus.control;
		var addAction = if (target.isNil) { \addToHead } { \addBefore }
		{ this.kr(Done.freeSelf) }.play(target ? Server.local, bus, addAction: addAction).onFree { bus.free };
		bus.asMap;
	}
}

+ Symbol {
	bus { |channels, rate="ar"| ^this.kr(0, spec: [0, 1000, \lin, channels, 0, "bus-" ++ rate]) }

	buf { |channels| ^this.kr(0, spec: [0, 1000, \lin, channels, 0, "buf"]) }

	bufpos { ^this.kr(0, spec: [0, 1000, \lin, 0.01, 0, "bufpos"]) }

	num { |default, min, max, warp, step, lag, color| ^this.kr(default, lag, false, [min, max, warp, step, default, color]) }

	name { ^this }

	note { |args| ^(type: \note, instrument: this).putAll(args) }

	postParams {
		SynthDescLib.global[this] !?
		{ |def| def.controls.do(_.postln) } ??
		{ postf("WARNING: SynthDef % not found\n", this) }
	}
}

+ SynthDef {
	publish { |color|
		var specs = (), specs_json;
		this.add;
		this.specs.keysValuesDo { |name, spec|
			specs[name] = spec.units.switch
			{ "bus-kr" } { (type:"bus", rate: "Control", channels: spec.step) }
			{ "bus-ar" } { (type: "bus", rate: "Audio", channels: spec.step) }
			{ "buf" } { (type: "buffer", channels: spec.step) }
			{ "bufpos" } { (type:"BufPos") }
			{ (
				type:"numerical", defaultValue: spec.default.asString, min: spec.minval.asString, max: spec.maxval.asString,
				warp: spec.warp.asSpecifier, step: spec.step.asString, associatedColor: spec.units)
			};
		};
		specs_json = JSONlib.convertToJSON(specs);
		specs_json.postln;
		Ponticello.sendMsg('/register_synth_def', this.name, specs_json, color)
	}
}

+ Event {
	* set { |synth, args| ^(type: \set, synth: synth).putAll(args) }

	* noteOn { |vst, note, velo| ^(type: \noteOn, vst: vst, note: note, velo: velo) }

	* noteOff { |vst, note, velo| ^(type: \noteOff, vst: vst, note: note, velo: velo) }

	asFade { |start, end| ^(type: \fade, time: start, end: end).putAll(this) }

	synthSet { |synth, t| ^(type: \set, synth: synth, time: t).putAll(this) }

	t { |time| this.put(\time, time) }
}

+ Function {
	t { |time| ^(type: \function, time: time, function: this) }

	routineEvent { |time, dur| ^(type: \routine, function: this, time: time, dur: dur) }
}

+ MIDIEndPoint {
	register { |key, type, func|
		^MIDIdef(key, func, srcID: this.uid, msgType: type)
	}

	onNoteOn { |key, func| ^this.register(key, \noteOn, func) }

	onNoteOff { |key, func| ^this.register(key, \noteOff, func) }
}
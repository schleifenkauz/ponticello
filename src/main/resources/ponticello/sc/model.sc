ParameterControl {
	var <name, <sound_proc;

	initialize { |proc, param_name|
		sound_proc = proc;
		name = param_name;
	}

	allocatesBus { false }

	getBus { |inst| if (this.allocatesBus) { inst.getControlBus(this) } { nil } }

	updateInstances { |func| sound_proc.updateInstances(func) }

	getValue { |inst, t| }

	getUGen { |inst| }

	prepare { |inst| }

	apply { |inst| }

	dispose { }
}

ValueControl : ParameterControl {
	var value, allocateBus, cutoff_multiplier, bus;

	* new { |value = 0, allocate_bus = false, cutoff_multiplier = 0|
		^super.new.init(value, allocate_bus, cutoff_multiplier)
	}

	init { |val = 0, use_bus = false, multiplier = 0|
		value = val;
		allocateBus = use_bus;
		cutoff_multiplier = multiplier;
	}

	getBus { |inst| bus ? super.getBus(inst) }

	getValue { |inst, t| value + (cutoff_multiplier * inst.cutoff) }

	getUGen { |inst|
		^if (allocateBus) {
			if (bus != nil) { bus.kr } { inst.getControlBus(this.name).kr };
		} { value }
	}

	initialize { |proc, name|
		super.initialize(proc, name);
		if (allocateBus && (cutoff_multiplier == 0)) {
			bus = Bus.control;
		}
	}

	allocatesBus { allocateBus && (cutoff_multiplier != 0) }

	apply { |inst|
		if (inst.type == \synth) {
			inst.putArgument(this.name, value);
		};
	}

	update { |value|
		if (allocateBus) {
			if (bus != nil) { bus.set(value); } {
				this.updateInstances { |inst|
					var val = value + (cutoff_multiplier * inst.cutoff);
					inst.getControlBus(this.name).set(val);
				};
			};
		} {
			if (this.sound_proc.type == \synth) {
				this.updateInstances { |inst|
					inst.putArgument(this.name, value);
				}
			}
		}
	}

	dispose {
		if (bus != nil) { bus.free; }
	}
}

BusControl : ParameterControl {
	var bus;

	* new { |bus|
		^super.new.init(bus)
	}

	init { |b| bus = b  }

	getBus { bus }

	getValue { |inst, t| bus.getSynchronous }

	getUGen { |inst| bus.kr }

	apply { |inst|
		inst.mapParameter(this.name, bus);
	}

	update { |new_bus|
		bus = new_bus;
		updateInstances { |inst|
			inst.mapParameter(this.name, bus);
		}
	}
}

EnvelopeControl : ParameterControl {
	classvar synth_def_ctr;
	var env, synth_def;

	* initClass {
		synth_def_ctr = 0;
	}

	* new { |env|
		^super.new.init(env)
	}

	init { |e| env = e }

	initialize { |proc, name|
		super.initialize(proc, name);
		synth_def = ("env" ++ synth_def_ctr).asSymbol;
		this.defineSynth;
	}

	allocatesBus { true }

	defineSynth {
		SynthDef(synth_def) { |out, cutoff = 0|
			var sig = IEnvGen.kr(env, index: Sweep.kr(rate: ~time_warp_bus.kr));
			Out.kr(out, sig);
		}.add;
	}

	getValue { |inst, t| ^env.at(t) }

	getUGen { |inst| ^inst.getControlBus(super.name).kr }

	prepare { |inst|
		var initial_value = env.at(inst.cutoff);
		inst.createControlBus(this.name, initial_value);
	}

	apply { |inst|
		if (inst.type == \synth) {
			var bus = inst.getControlBus(this.name);
			inst.createAuxilSynth(this.name, synth_def, [out: bus, cutoff: inst.cutoff]);
			inst.mapParameter(this.name, bus);
		}
	}

	update { |new_env|
		env = new_env;
		if (this.sound_proc.type == \synth) {
			this.defineSynth;
			this.updateInstances { |inst|
				var bus = inst.getControlBus(this.name);
				inst.replaceAuxilSynth(this.name, synth_def, [out: bus, cutoff: inst.cutoff])
			};
		}
	}


	dispose {
		if (this.sound_proc.type == \synth) {
			this.updateInstances { |inst|
				inst.freeAuxilSynth(this.name);
			}
		}
	}
}

LFOControl : ParameterControl {
	var references, ugen_func, synth_def;

	* new { |references, ugen_func|
		^super.new.init(references, ugen_func);
	}

	init { |refs, func|
		references = refs;
		ugen_func = func
	}

	initialize { |proc, name|
		super.initialize(proc, name);
		this.defineSynth;
	}

	allocatesBus { true }

	defineSynth {
		SynthDef(synth_def) { |out, cutoff|
			var sig = ugen_func.value(cutoff);
			Out.kr(out, sig);
		}.add;
	}

	getUGen { |inst| ^inst.getControlBus(super.name).kr }

	getValue { |inst| ^inst.getControlBus(super.name).getSynchronous }

	prepare { |inst|
		inst.createControlBus(this.name);
	}

	apply { |inst|
		var bus = inst.getControlBus(this.name);
		this.prCreateSynth(inst, bus, replace: false);
		inst.mapParameter(this.name, bus);
	}

	prCreateSynth { |inst, bus, replace|
		var args = [out: bus, cutoff: inst.current_time];
		var synth = if (replace) {
			inst.replaceAuxilSynth(this.name, synth_def, args);
		} {
			inst.createAuxilSynth(this.name, synth_def, args);
		};
		references.do { |ref|
			var b = inst.def.getControl(ref).getBus(inst);
			if (b != nil) {
				synth.map(ref, b);
			} {
				synth.set(ref, inst.getControlValue(ref));
			}
		}
	}

	update { |func|
		ugen_func = func;
		this.defineSynth;
		this.updateInstances { |inst|
			var bus = inst.getControlBus(this.name);
			this.prCreateSynth(inst, bus, replace: true);
		}
	}

	dispose {
		this.updateInstances { |inst|
			inst.freeAuxilSynth(this.name);
		}
	}
}

ExprControl : ParameterControl {
	var func;

	* new { |func|
		^super.new.init(func);
	}

	init { |f| func = f }

	getValue { |inst, t| func.value(inst, t) }

	getUGen { |inst, t| this.getValue(inst, t) }

	apply { |inst|
		inst.putArgument(func.value(inst, 0));
	}

	update { |new_func|
		func = new_func;
		if (this.sound_process.type == \synth) {
			this.updateInstances { |inst|
				inst.putArgument(func.value(inst, inst.current_time));
			}
		}
	}
}

SoundProcess {
	classvar <dict;
	var <>name, <def, <duration, <controls, instances, instance_ctr;

	* initClass {
		dict = Dictionary.new;
	}

	* rename { |old_name, new_name|
		var proc = dict.removeAt(name);
		proc.name = new_name;
		dict[name] = proc;
	}

	* create { |name, def, duration, controls|
		var proc = super.new.init(name, def, duration, controls);
		dict[name] = proc;
		^proc;
	}

	* get { |name| ^dict[name] }

	init { |n, d, dur, ctrls|
		name = n; def = d; duration = dur; controls = ctrls;
		instances = (); instance_ctr = 0;
		controls.keysValuesDo { |param, ctrl| ctrl.initialize(this, param) };
	}

	type {
		^if (def.isMemberOf(Symbol)) { \synth } { \routine }
	}

	getInstance { |idx| ^instances[idx] }

	getControl { |parameter| ^controls[parameter] }

	addControl { |parameter, ctrl, idx|
		ctrl.initialize(this, parameter);
		controls[parameter] = ctrl;
		this.updateInstances { |inst|
			ctrl.prepare(inst);
			ctrl.apply(inst);
		}
	}

	removeControl { |parameter, defaultValue|
		var ctrl = controls.removeAt(parameter);
		this.updateInstances { |inst|
			inst.putArgument(parameter, defaultValue);
		};
		ctrl.dispose;
	}

	replaceControl { |parameter, new_ctrl|
		var old_ctrl = controls[parameter];
		new_ctrl.initialize(this, parameter);
		controls.put(parameter, new_ctrl);
		if (old_ctrl.allocatesBus && new_ctrl.allocatesBus.not) {
			this.updateInstances { |inst|
				inst.freeControlBus(parameter);
			};
		};
		if (new_ctrl.allocatesBus && old_ctrl.allocatesBus.not) {
			this.updateInstances { |inst|
				new_ctrl.prepare(inst);
			}
		};
		old_ctrl.dispose;
		this.updateInstances { |inst|
			new_ctrl.apply(inst);
		}
	}

	createInstance { |score_y, cutoff, midi_note|
		var inst = SoundProcessInstance.new(this, instance_ctr, score_y, cutoff ? 0, midi_note);
		instances.put(instance_ctr, inst);
		instance_ctr = instance_ctr + 1;
		^inst
	}

	startNewInstance { |score_y, cutoff, midi_note, server_latency, player_id|
		var inst = this.createInstance(cutoff, midi_note, player_id);
		var placement = AudioNodeOrder.insert(inst, score_y);
		inst.start(placement, server_latency, player_id);
	}

	updateInstances { |func|
		instances.do(func);
	}

	removeInstance { |idx|
		instances.remove(idx)
	}

	clearAll {}
}

SoundProcessInstance : AudioNode {
	var <def, idx, cutoff, score_y, midi_note,
	start_time, control_buses, auxil_synths, group, <synth, routine;

	* new { |def, idx, score_y, cutoff = 0, midi_note = nil|
		^super.new.init(def, idx, score_y, cutoff, midi_note);
	}

	init { |d, i, y, offset, midi|
		def = d; idx = i; score_y = y; cutoff = offset; midi_note = midi;
		control_buses = (); auxil_synths = ();
	}

	type { ^def.type }

	score_y { ^score_y }

	current_time { TempoClock.beats - start_time + cutoff }

	createControlBus { |name, initial_value|
		var bus = Bus.control(Server.local, 1);
		bus.set(initial_value);
		control_buses[name] = bus;
		^bus
	}

	getControl { |name| ^def.getControl(name) }

	getControlBus { |name| ^control_buses[name] }

	freeControlBus { |name|
		var bus = control_buses.removeAt(name);
		bus.free;
	}

	createAuxilSynth { |param_name, synth_def, args, replace = false|
		var synth = if (this.type == \synth) {
			if (synth == nil) { Error("Cannot create auxiliary synth because, this.synth is nil.").throw };
			Synth.newPaused(synth_def, args, target: synth, addAction: \addBefore)
		} { Synth.newPaused(synth_def, args, target: group, addAction: \addToTail) };
		auxil_synths[param_name] = synth;
		^synth
	}

	replaceAuxilSynth { |parameter, synth_def, args|
		var old_synth = auxil_synths[parameter];
		var new_synth = Synth.replace(old_synth, synth_def, args);
		auxil_synths[parameter] = new_synth;
		^new_synth
	}

	freeAuxilSynth { |name|
		var s = auxil_synths.removeAt(name);
		s.free;
	}

	mapParameter { |name, bus|
		if (this.type == \synth) {
			if (synth == nil) { Error("Cannot map parameter because this.synth is nil.").throw };
			synth.map(name, bus);
		}
	}

	putArgument { |name, value|
		if (synth != nil) {
			synth.set(name, value);
			postf("Set % = %\n", name, value);
		};
	}

	setDefaultValue { |parameter|
		if (synth != nil) {
			var def = SynthDescLib.global.at(synth.defName);
			var default = def.controlDict[parameter].defaultValue;
			synth.set(parameter, default);
		} {
			Error("default value for routines not implemented").throw;
		}
	}

	getControlValue { |name| def.getControl(name).getValue(this) }

	getControlUGen { |name| def.getControl(name).getUGen(this) }

	dispose {
		~ponticello_addr.sendMsg('/freed', -1, def.name, idx);
		control_buses.do(_.free);
		auxil_synths.do(_.free);
		AudioNodeOrder.remove(this);
	}

	start { |placement, latency, player_id|
		var duration = def.duration !? { def.duration - cutoff };
		start_time = TempoClock.beats;
		group = Group.new(placement.target, placement.addAction);
		def.controls.do { |ctrl| ctrl.prepare(this) };
		if (this.type == \synth) {
			var auto_release = (duration != nil).asInteger;
			var args = [duration: duration ? inf, auto_release: auto_release].postln;
			synth = Synth.newPaused(def.def, args, group, \addToTail);
		};
		def.controls.do { |ctrl| ctrl.apply(this) };
		if (this.type == \synth) {
			var start = {
				Server.local.sync;
				auxil_synths.do(_.run);
				synth.run;
			};
			if (latency != 0) {
				latency = latency - (TempoClock.beats - start_time);
				Server.local.makeBundle(latency, start);
			} {
				start.value();
			};
			synth.onFree { this.dispose };
		} {
			routine = Task {
				def.value(this);
				this.dispose;
			}.play;

		}
	}

	run { |active|
		auxil_synths.do { |s| s.run(active) };
		if (synth != nil) { synth.run(active) } {
			if (routine != nil) {
				if (active) { routine.play } { routine.pause };
			} {
				Error("Not initialized").throw;
			}
		}
	}

	release {
		def.removeInstance(idx);
		if (synth != nil) { synth.release } { routine.stop }
	}
}
SoundProcess {
	classvar dict;
	var <>name, <def, <>duration, <controls, control_map, instances, byPosition, instance_ctr;

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
		instances = Dictionary.new; byPosition = Dictionary.new; control_map = Dictionary.new;
		instance_ctr = 0;
		controls.do { |ctrl|
			ctrl.sound_proc = this;
			control_map[ctrl.name] = ctrl;
		};
	}

	* stopAllProcesses { |player_id|
		dict.do { |proc|
			proc.stopAllInstances(player_id)
		}
	}

	stopAllInstances { |player_id|
		instances.copy.do { |inst|
			if (inst.playerId == player_id) { inst.release; }
		};
	}

	type {
		^if (def.isMemberOf(Symbol)) { \synth } { \routine }
	}

	getInstance { |idx| ^instances[idx] }

	getInstanceAt { |pos| ^byPosition[pos] }

	getSingleInstance {
		if (instances.size != 1) {
			Error("No single instance of SoundProcess '%'. #instances = %".format(name, instances.size))
		};
		^instances.values[0]
	}

	getControl { |parameter| ^control_map[parameter] }

	addControl { |ctrl, idx|
		ctrl.sound_proc = this;
		controls.insert(idx, ctrl);
		control_map[ctrl.name] = ctrl;
		this.updateInstances { |inst|
			ctrl.prepare(inst);
			ctrl.apply(inst);
		}
	}

	removeControl { |parameter, defaultValue|
		var ctrl = control_map.removeAt(parameter);
		controls.remove(ctrl);
		this.updateInstances { |inst|
			inst.putArgument(parameter, defaultValue);
		};
		ctrl.dispose;
	}

	moveControl { |parameter, idx|
		var ctrl = control_map[parameter];
		var old_idx = controls.indexOf(ctrl);
		controls.removeAt(old_idx);
		controls.insert(idx, ctrl);
	}

	replaceControl { |new_ctrl|
		var parameter = new_ctrl.name;
		var old_ctrl = control_map[parameter];
		var idx = controls.indexOf(old_ctrl);
		new_ctrl.sound_proc = this;
		control_map[parameter] = new_ctrl;
		controls[idx] = new_ctrl;

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

	createInstance { |pos, cutoff, midi_note|
		var inst = SoundProcessInstance.new(this, instance_ctr, pos, cutoff ? 0, midi_note);
		instances.put(instance_ctr, inst);
		byPosition[pos] = inst;
		instance_ctr = instance_ctr + 1;
		^inst
	}

	startNewInstance { |pos, cutoff, midi_note, server_latency, player_id|
		var inst = this.createInstance(pos, cutoff, midi_note);
		var placement = AudioNodeOrder.insert(inst);
		inst.start(placement, server_latency, player_id);
	}

	updateInstances { |func|
		instances.do(func);
	}

	removeInstance { |idx|
		var inst = instances.removeAt(idx);
		byPosition.removeAt(inst.pos);
	}

	clearAll {}
}

SoundProcessInstance : AudioNode {
	var <def, idx, <cutoff, <pos, midi_note, <playerId,
	start_time, control_buses, auxil_synths, group, <synth, routine, on_dispose;

	* new { |def, idx, pos, cutoff = 0, midi_note = nil|
		^super.new.init(def, idx, pos, cutoff, midi_note);
	}

	init { |d, i, p, offset, midi|
		def = d; idx = i; pos = p; cutoff = offset; midi_note = midi;
		control_buses = (); auxil_synths = (); on_dispose = [];
	}

	type { ^def.type }

	score_y { ^pos.y }

	current_time { ^TempoClock.beats - start_time + cutoff }

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
		var auxil_synth;
		auxil_synth = if (this.type == \synth) {
			if (synth == nil) { Error("Cannot create auxiliary synth because, this.synth is nil.").throw };
			Synth.newPaused(synth_def, args, target: synth, addAction: \addBefore)
		} { Synth.newPaused(synth_def, args, target: group, addAction: \addToTail) };
		auxil_synths[param_name] = auxil_synth;
		^auxil_synth
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

	getControlValue { |name| ^def.getControl(name).getValue(this) }

	getControlUGen { |name| ^def.getControl(name).getUGen(this) }

	onDispose { |func|
		on_dispose.add(func);
	}

	dispose {
		~ponticello_addr.sendMsg('/freed', -1, def.name, idx);
		control_buses.do(_.free);
		group.free;
		on_dispose.do(_.value);
		AudioNodeOrder.remove(this);
		def.removeInstance(idx);
	}

	start { |placement, latency, player_id, run = true|
		var duration = def.duration !? { def.duration - cutoff };
		start_time = TempoClock.beats;
		playerId = player_id;
		group = Group.new(placement.target, placement.addAction);
		def.controls.do { |ctrl| ctrl.prepare(this) };
		if (this.type == \synth) {
			var auto_release = (duration != nil).asInteger;
			var args = [duration: duration ? inf, auto_release: auto_release];
			synth = Synth.newPaused(def.def, args, group, \addToTail);
		};
		def.controls.do { |ctrl| ctrl.apply(this) };
		if (this.type == \synth) {
			if (run) {
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
			};
			synth.onFree { this.dispose };
		} {
			routine = Task {
				def.value(this);
				this.dispose;
			};
			if (run) { routine.play };
		}
	}

	setRunning { |active|
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
		if (synth != nil) { synth.release };
		if (routine != nil) { routine.stop };
	}
}
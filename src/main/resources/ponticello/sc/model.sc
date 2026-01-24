SoundProcess {
	classvar dict;
	var <>name, <instr, <>duration, <controls, control_map, instances, byPosition, instance_ctr;

	* initClass {
		dict = Dictionary.new;
	}

	* rename { |old_name, new_name|
		var proc = dict[old_name];
		if (proc == nil) {
			Error("SoundProcess % not found".format(old_name)).throw;
		};
		dict.removeAt(old_name);
		proc.name = new_name;
		dict[new_name] = proc;
	}

	* create { |name, instr, duration, controls|
		var proc = super.new.init(name, instr, duration, controls);
		dict[name] = proc;
		^proc;
	}

	* remove { |name|
		dict.removeAt(name); 
	}

	* get { |name| ^dict[name] }

	init { |n, i, dur, ctrls|
		name = n; instr = i; duration = dur; controls = ctrls;
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
			if (player_id == nil || inst.playerId == player_id) { inst.release; }
		};
	}

	type { ^instr.type }

	setInstrument { |i|
		instr = i;
		instances.do { |inst|
			inst.restart;
		}
	}

	getInstance { |idx| ^instances[idx] }

	getInstanceAt { |pos| ^byPosition[pos] }

	getSingleInstance {
		if (instances.size != 1) {
			Error("No single instance of SoundProcess '%'. #instances = %".format(name, instances.size))
		};
		^instances.values[0]
	}

	getControl { | parameter |
		^control_map[parameter] 
		?? { instr.getDefaultValue(parameter) !? {| default | ValueControl.new(default) } }
	}

	addControl { |ctrl, idx|
		ctrl.sound_proc = this;
		controls = controls.insert(idx, ctrl);
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
		controls = controls.insert(idx, ctrl);
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

	createInstance { |pos, cutoff = 0, extra_args|
		var inst = SoundProcessInstance.new(this, instance_ctr, pos, cutoff ? 0, extra_args ? ());
		instances.put(instance_ctr, inst);
		if (pos != nil) {
			byPosition[pos] = inst;
		};
		instance_ctr = instance_ctr + 1;
		^inst
	}

	startNewInstance { |pos, cutoff, extra_args, server_latency, player_id|
		var inst = this.createInstance(pos, cutoff, extra_args);
		var placement = if (this.type != \vst_midi) { AudioNodeOrder.insert(inst) };
		//postf("Placement for %: %\n", name, placement);
		inst.start(placement, server_latency, player_id);
		^inst.idx;
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
	var <def, <idx, <cutoff, <pos, 
	<extra_args, <playerId, 
	<server_latency, start_time, placement, group,
	running = false, restarting = false, disposed = false,
	<initial_args, control_buses, auxil_synths, sound_obj,
	on_dispose;

	* new {| def, idx, pos, cutoff = 0, extra_args |
		^ super.new.init (def, idx, pos, cutoff, extra_args);
	}

	init { |d, i, p, offset, extra|
		def = d; idx = i; pos = p; cutoff = offset; extra_args = extra;
		initial_args = Dictionary.new; control_buses = Dictionary.new; auxil_synths = Dictionary.new; 
		on_dispose = [];
	}

	type { ^def.type }

	score_y { ^pos.y }

	node { ^group }

	current_time { ^TempoClock.beats - start_time + cutoff }

	createControlBus { |name, initial_value|
		var bus = Bus.control(Server.local, 1);
		bus.set(initial_value);
		control_buses[name] = bus;
		^bus
	}

	getControl { |name|
		^if (extra_args.includesKey(name)) {
			ValueControl.new(name, extra_args[name])
		} { def.getControl(name) }
	}

	getControlBus { |name| ^control_buses[name] }

	getInitialArguments { ^initial_args ++ extra_args }

	freeControlBus { |name|
		var bus = control_buses.removeAt(name);
		bus.free;
	}

	createAuxilSynth { |param_name, synth_def, args, replace = false|
		var auxil_synth;
		if (sound_obj == nil) {Error ("Cannot create auxiliary synth because sound_obj = nil.").throw};
		auxil_synth = if (sound_obj.isMemberOf (Synth) ) {
			Synth.newPaused (synth_def, args, target: sound_obj, addAction: \addBefore)
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
		if (extra_args.includesKey(name).not) {
			if (sound_obj == nil) {Error ("Cannot map parameter because sound_obj = nil.").throw};
			if (sound_obj.respondsTo (\map) ) {
				sound_obj.map (name, bus);
			}
		}
	}

	putArgument { |name, value|
		if (extra_args.includesKey(name).not) {
			if (sound_obj == nil) {
				initial_args.put(name, value);
			} {
				if (sound_obj.respondsTo(\set)) {
					sound_obj.set(name, value);
				}
			}
		}
	}

	setDefaultValue { |parameter|
		sound_obj.set (parameter, def.instr.getDefaultValue (parameter) )
	}

	getControlValue { |name|
		^extra_args[name] ?? {(def.getControl(name) !? { |ctrl| ctrl.getValue(this) })}
	}

	getControlUGen { |name|
		^extra_args[name] ?? {def.getControl(name) !? { |ctrl| ctrl.getUGen(this) }}
	}

	onDispose { |func|
		on_dispose = on_dispose.add(func);
	}

	dispose {
		if (disposed.not) {
			control_buses.do (_.free);
			control_buses = Dictionary.new;
			if (group != nil) {
				group.free;
				group = nil;
				if (pos != nil) {
					AudioNodeOrder.remove (this);
				}
			};
			on_dispose.do (_.value);
			if (restarting.not) {
				~ponticello_addr.sendMsg ('/stopped', -1, def.name, idx);
				def.removeInstance(idx);
				disposed = true;
			}
		}
	}

	start { |plcmnt, latency, player_id, run = true|
		var duration = def.duration !? { |dur| dur - cutoff };
		placement = plcmnt;
		start_time = TempoClock.beats;
		server_latency = latency;
		playerId = player_id;
		running = run;
		if (placement != nil) {
			Server.local.sync;
			group = Group.new(placement.target, placement.addAction);
		};
		def.controls.do { |ctrl|
			if (extra_args.includesKey(ctrl.name).not) { ctrl.prepare(this) };
		};
		sound_obj = def.instr.create(this);
		Server.local.sync;
		def.controls.do { |ctrl|
			if (extra_args.includesKey(ctrl.name).not) { ctrl.apply(this) };
		};
		if (run) {
			Server.local.sync;
			Server.local.makeBundle (latency) {
				auxil_synths.do (_.run);
				sound_obj.run(true, this);
			}
		}
	}

	restart {
		restarting = true;
		this.release;
		cutoff = this.current_time;
		this.start(placement, 0, playerId, run: running);
		restarting = false;
	}

	run { |active|
		running = active;
		auxil_synths.do { |s| s.run(active) };
		sound_obj.run (active, this);
	}

	release {
		if (sound_obj.isMemberOf (Synth) ) {
			sound_obj.set (\gate, 0); //why does [release] not work here?
		} {
			sound_obj.release;
			this.dispose;
		}
	}
}
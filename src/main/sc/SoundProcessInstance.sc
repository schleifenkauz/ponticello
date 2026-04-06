SoundProcessInstance : AudioNode {
	var <def, <idx, <pos, <cutoff, extra_control_map,
	control_buses, auxil_synths, <children, on_dispose,
	<player_id, <>server_latency, start_time, placement, group,
	running = false, <restarting = false, disposed = false,
	sound_obj, <midi_track,
	<>clock_time, <>parent_instance;

	* new {| def, idx, pos, cutoff = 0, extra_controls |
		var extra_control_map = Dictionary.new;
		extra_controls.do { |ctrl| extra_control_map[ctrl.name] = ctrl };
		^super.newCopyArgs(
			def, idx, pos, cutoff, extra_control_map,
			Dictionary.new, Dictionary.new, List[], List[]
		);
	}

	type { ^def.type }

	score_y { ^pos.y }

	node { ^group }

	//TODO when to subtract server latency?
	current_time { ^clock_time ?? { TempoClock.beats - start_time + cutoff }}

	isActive {
	    ^running && (this.current_time < def.duration)
	}

	control_map { ^def.control_map ++ extra_control_map }

	extra_control { |name| ^extra_control_map[name] }

	controls_do { |func|
		def.control_map.keysValuesDo { |name, ctrl|
			if (extra_control_map.includesKey(name).not) {
				func.value(ctrl);
			}
		};
		extra_control_map.do(func);
	}

	getControl { |name|	^extra_control_map[name] ?? { def.control_map[name] } }

	getControlValue { |name|
		^this.getControl(name) !? (_.getValue(this)) ? def.instr.getDefaultValue(name);
	}

	get { |name| ^this.getControlValue(name) }

	getControlUGen { |name|
		^this.getControl(name) !? (_.getUGen(this)) ? def.instr.getDefaultValue(name);
	}

	getControlBus { |name| ^control_buses[name] }

	createControlBus { |name, initial_value|
		^if (control_buses.includesKey(name).not) {
			var bus = Bus.control(Server.local, 1);
			bus.set(initial_value);
			control_buses[name] = bus;
			bus
		} {
			var bus = control_buses[name];
			if (initial_value.notNil) { bus.set(initial_value) };
			bus
		}
	}

	freeControlBus { |name|
		var bus = control_buses.removeAt(name);
		bus.free;
	}

	createAuxilSynth { |param_name, synth_def, args, replace = false|
		var auxil_synth = if (running) {
			Synth(synth_def, args, target: group, addAction: \addToTail);
		} { Synth.newPaused(synth_def, args, group, \addToTail) };
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

	mapParameter { |name, bus, src_control|
		if (sound_obj == nil) {Error ("Cannot map parameter because sound_obj = nil.").throw};
		if (sound_obj.respondsTo (\map) ) {
			sound_obj.map (name, bus);
		}
	}

	set { |name, value, src_control|
		if (sound_obj.respondsTo(\set)) {
			sound_obj.set(name, value);
		}
	}

	addControl { |ctrl|
		extra_control_map[ctrl.name] = ctrl;
		ctrl.sound_proc = def;
	}

	setDefaultValue { |parameter|
		if (extra_control_map.includesKey(parameter).not && sound_obj.respondsTo(\set)) {
			var default = def.instr.getDefaultValue(parameter);
			sound_obj.set(parameter)
		}
	}

	updateDuration { |dur|
		if (sound_obj.isKindOf(Synth)) {
			sound_obj.set(\duration, dur);
		}
	}

	asString { ^"Instance #% of % [running: %, disposed: %]".format(idx, def, running, disposed) }

	onDispose { |func|
		on_dispose.add(func);
	}

	dispose {
		case
		{ disposed } { postf("Warning: % already disposed", this) }
		{ restarting } {
			fork {
				this.prStart(running);
				restarting = false;
			};
		}
		{ true } {
			control_buses.do (_.free);
			control_buses = Dictionary.new;
			if (group != nil) {
				if (midi_track.isNil && parent_instance.isNil && pos.notNil) {
					AudioNodeOrder.remove(this);
				};
				group.free;
				group = nil;
			};
			sound_obj = nil;
			on_dispose.do(_.value);
            Ponticello.sendMsg ('/stopped', -1, def.name, idx);
            def.removeInstance(idx);
            disposed = true;
		}
	}

	start { |plcmnt, latency, playerId, midiTrack=nil, run = true|
		var duration = def.duration !? { |dur| dur - cutoff };
		placement = plcmnt;
		start_time = TempoClock.beats;
		server_latency = latency ? Server.local.latency;
		player_id = playerId ? -1;
		midi_track = midiTrack;
		if (placement.notNil && (def.type != \midi)) {
			group = Group.new(placement.target, placement.addAction);
		};
		forkIfNeeded {
			if (placement.notNil && (def.type != \midi)) {
				Server.local.sync;
				if (midiTrack.isNil && pos.notNil && parent_instance.isNil) {
					Ponticello.sendMsg('/started_sound_proc', this.nodeID, def.name, pos.t, pos.y);
				};
			};
			this.controls_do(_.prepare(this));
			this.prStart(run);
		}
	}

	prStart { |run|
		this.controls_do(_.apply(this));
		sound_obj = def.instr.create(this);
        if (run) {
            Server.local.sync;
            Server.local.makeBundle(server_latency) {
                if (restarting.not) {
					auxil_synths.do (_.run);
				};
				sound_obj.run(true);
            };
			running = true;
        }
	}

	restart {
        restarting = true;
		sound_obj.release;
		sound_obj = nil;
	}

	run { |active|
		running = active;
		auxil_synths.do { |s| s.run(active) };
		sound_obj.run(active, this);
	}

	release { |latency|
		if (sound_obj.notNil) {
			latency = latency ? server_latency;
			children.do(_.release(latency));
			if (sound_obj.isMemberOf(Synth)) {
				Server.local.makeBundle(latency) {
					sound_obj.release;
				}
			} {
				postf("Release %\n", sound_obj);
				sound_obj.release(latency);
			};
		} {
			postf("WARNING: Already released %\n", this);
		}
	}

	startChildInstance { |proc, extra_controls|
		var inst, placement, p;
		if (this.type != \routine) {
			Exception("Attempt to call .startChildInstance on %".format(this)).throw;
		};
		p = (t: pos.t + this.current_time, y: pos.y);
		//postf("Creating child instance %, with %\n", proc, extra_controls);
		inst = proc.createInstance(p, 0, extra_controls);
		inst.parent_instance = this;
		children.add(inst);
		inst.onDispose { children.remove(inst) };
		if (clock_time.isNil) {
			var placement = (addAction: \addToTail, target: group);
			//postf("Starting child % at %\n", inst, placement);
			inst.start(placement, server_latency, player_id);
		};
		^inst
	}
}
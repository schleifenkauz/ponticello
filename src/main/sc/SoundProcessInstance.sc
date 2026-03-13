

SoundProcessInstance : AudioNode {
	var <def, <idx, <cutoff, <pos,
	<extra_args, <playerId,
	<server_latency, start_time, placement, group,
	running = false, <restarting = false, disposed = false,
	<initial_args, control_buses, auxil_synths, sound_obj, children,
	<midiTrack, <midiSrc,
	on_dispose;

	* new {| def, idx, pos, cutoff = 0, extra_args |
		^ super.new.init (def, idx, pos, cutoff, extra_args);
	}

	init { |d, i, p, offset, extra|
		def = d; idx = i; pos = p; cutoff = offset; extra_args = extra;
		initial_args = Dictionary.new; control_buses = Dictionary.new; auxil_synths = Dictionary.new;
		on_dispose = [];
	}

	setupMidi { |track, src|
		midiTrack = track;
		midiSrc = src;
	}

	type { ^def.type }

	score_y { ^pos.y }

	node { ^group }

	current_time { ^TempoClock.beats - start_time + cutoff - Server.local.latency }

	isActive {
	    ^running && (this.current_time < def.duration)
	}

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
		var auxil_synth, target, addAction;
		if (sound_obj == nil) { Error("Cannot create auxiliary synth because sound_obj = nil.").throw };
		target = if (sound_obj.isMemberOf(Synth)) { sound_obj } { group };
		addAction = if (sound_obj.isMemberOf(Synth)) { \addBefore } { \addToTail };
		auxil_synth = if (running) {
			Synth(synth_def, args, target, addAction);
		} { Synth.newPaused(synth_def, args, target, addAction) };
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
		^extra_args[name] ?? {
		    var ctrl = def.getControl(name);
		    if (ctrl.notNil) { ctrl.getValue(this) } { def.instr.getDefaultValue(name) }
		}
	}

	get { |name| ^this.getControlValue(name) }

	getControlUGen { |name|
		^extra_args[name] ?? {
		    var ctrl = def.getControl(name);
		    if (ctrl.notNil) { ctrl.getUGen(this) } { def.instr.getDefaultValue(name) }
		}
	}

	updateDuration { |dur|
		if (sound_obj.isKindOf(Synth)) {
			sound_obj.set(\duration, dur);
		}
	}

	asString { ^"Instance #% of % [running: %, disposed: %]".format(idx, def, running, disposed) }

	onDispose { |func|
		on_dispose = on_dispose.add(func);
	}

	dispose {
		case
		{ disposed } { postf("Warning: % already disposed", this) }
		{ restarting } {
			{
				this.prStart(running);
				restarting = false;
			}.fork;
		}
		{ true } {
			control_buses.do (_.free);
			control_buses = Dictionary.new;
			if (group != nil) {
				group.free;
				group = nil;
				if (pos != nil) {
					AudioNodeOrder.remove(this);
				}
			};
			on_dispose.do (_.value);
            ~ponticello_addr.sendMsg ('/stopped', -1, def.name, idx);
            def.removeInstance(idx);
            disposed = true;
		}
	}

	start { |plcmnt, latency, player_id, run = true|
		var duration = def.duration !? { |dur| dur - cutoff };
		placement = plcmnt;
		start_time = TempoClock.beats;
		server_latency = latency;
		playerId = player_id;
		if (placement != nil) {
			Server.local.sync;
			group = Group.new(placement.target, placement.addAction);
			//group.register(assumePlaying: true);
		};
		def.controls.do { |ctrl|
			if (extra_args.includesKey(ctrl.name).not) { ctrl.prepare(this) };
		};
		this.prStart(run);
	}

	prStart { |run|
	    sound_obj = def.instr.create(this);
		Server.local.sync;
		def.controls.do { |ctrl|
			if (extra_args.includesKey(ctrl.name).not) { ctrl.apply(this) };
		};
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

	release {
		children.do(_.release);
		if (sound_obj.isMemberOf(Synth) ) {
			sound_obj.set(\gate, 0); //why does [release] not work here?
		} {
			sound_obj.release;
		};
	}

	startChildInstance { |proc, extra_args|
		var inst, placement, p;
		if (this.type != \routine) {
			Exception("Attempt to call .startChildInstance on %".format(this)).throw;
		};
		p = (t: pos.t + this.current_time, y: pos.y);
		inst = proc.createInstance(p, 0, extra_args ? ());
		placement = (addAction: \addToTail, target: group);
		inst.start(placement, Server.local.latency, playerId);
		inst.onDispose { children.remove(inst) };
		children = children.add(inst);
		^inst
	}
}
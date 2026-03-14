SoundProcess {
	classvar dict, by_instrument, conds;
	var <>name, <instr, <duration, <controls, control_map, instances, byPosition, instance_ctr;

	* initClass {
		dict = Dictionary.new;
		by_instrument = Dictionary.new;
		conds = Dictionary.new;
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

	* create { |name, instr, controls, duration|
		var proc = super.new.init(name, instr, controls, duration);
		dict[name] = proc;
		if (conds.includesKey(name)) {
			var cond = conds.removeAt(name);
			cond.test = true;
			cond.signal;
		};
		if (by_instrument.includesKey(instr)) {
			by_instrument[instr] = by_instrument[instr].add(proc);
		} {
			by_instrument[instr] = [proc];
		};
		^proc;
	}

	* remove { |name|
		var proc = dict.removeAt(name);
		by_instrument[proc.instr].remove(proc);
	}

	* includesKey { |name| ^dict.includesKey(name) }

	* undefined { |name|
		var cond = conds[name];
		if (cond.notNil) {
			cond.test = true;
			cond.signal;
		} {
			postf("Warning: SoundProcess % was not queried");
		};
	}

	* get { |name|
		var proc;
		name = name.asSymbol;
		if (dict.includesKey(name).not) {
			var cond = Condition.new;
			conds[name] = cond;
			~ponticello_addr.sendMsg('/sync_sound_proc', name);
			cond.wait;
		};
		proc = dict[name.asSymbol];
		if (proc.isNil) {
			Exception("SoundProcess % not found".format(name)).throw;
		};
		^proc;
	}

	init { |n, i, ctrls, dur|
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

	* updatedInstrument { |instr|
	    if (by_instrument.includesKey(instr)) {
	        by_instrument[instr].do { |proc|
	            proc.updateInstances { |inst|
	                inst.restart;
	            }
	        }
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

	duration_ { |dur|
		duration = dur;
		this.updateInstances { |inst| inst.updateDuration(dur) };
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
		this.updateInstances { |inst|
			new_ctrl.prepare(inst);
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
		var placement = AudioNodeOrder.insert(inst);
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

	asString { ^"SoundProcess '%' [instr: %, duration: %]".format(name, instr, duration) }
}
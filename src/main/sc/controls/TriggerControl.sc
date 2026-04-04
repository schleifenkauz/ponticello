TriggerControl : ParameterControl {
	var bus, <>mingap = 0.01, state = 0, listeners;

	* new { |name|
		^super.new(name).init;
	}

	init {
		bus = Bus.control(Server.local);
		listeners = List[];
	}

	getBus { ^bus }

	getValue { bus.getValue }

	getUGen { ^bus.kr }

	getSynthArgument { ^bus.asMap }

	trigger {
		if (state != 1) {
			state = 1;
			bus.set(1);
			listeners.do(_.value);
			SystemClock.sched(mingap) {
				state = 0;
				bus.set(0);
			}
		};
	}

	onTrig { |inst, action|
		listeners.add(action);
		inst.onDispose {
			listeners.remove(action);
		}
	}

	dispose { bus.free; }
}

+SoundProcessInstance {
	onTrig { |param, action|
		this.getControl(param).onTrig(this, action);
	}
}

+SoundProcess {
	trigger { |parameter|
		var ctrl = this.getControl(parameter);
		if (ctrl.respondsTo(\trigger)) {
			ctrl.trigger;
		} {
			postf("WARNING: control % does not respond to .trigger\n", ctrl);
		}
	}
}
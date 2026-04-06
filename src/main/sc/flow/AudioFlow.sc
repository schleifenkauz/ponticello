AudioFlow : NamedObject {
	classvar dict;
	var <active = false, <node, recreating = false;

	* dict { ^dict ?? { dict = Dictionary.new } }

	* override { ^\ok }

	* newCopyArgs { arg ...args;
		var name = args[0];
		^super.newCopyArgs(name, false, nil, false, *args[1..])
	}

	create { |target, addAction|
		node = this.createNode(target, addAction);
		node.onFree {
			if (recreating) {
				recreating = false;
			} {
				node = nil;
			}
		};
	}

	recreate {
		recreating = true;
		protect {
			if (node.notNil) {
				this.create(node, \addReplace);
			} {
				postf("WARNING: Cannot recreate %. Node already freed.\n", this);
			}
		} {
			recreating = false;
		}
	}

	active_ { |enable, notify|
		if (active != enable) {
			active = enable;
			postf("Set active % = % (%)\n", this, enable, node);
			if (node.isKindOf(Synth)) {
				node.run(enable);
			};
			if (node.notNil && (notify != false)) {
				Ponticello.sendMsg('/set_flow_active', name, enable);
			}
		}
	}

	createNode {
		Exception("% does not override createNode".format(this.class)).throw;
	}

	release {
		if (node.isKindOf(Synth)) {
			node.release;
		}
	}

	free {
		this.release;
		super.free;
	}

	prCreateSynth { |def, args, target, addAction|
		^if (active) {
			Synth.new(def, args, target, addAction)
		} {
			Synth.newPaused(def, args, target, addAction)
		}
	}
}
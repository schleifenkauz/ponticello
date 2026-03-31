SynthInstrument {
	var synthDefName, synthDesc;
	classvar byName;

	* initClass {
		byName = Dictionary.new;
	}

	* new {| synthDef |
		^byName[synthDef] ?? {
			var desc = SynthDescLib.global.at(synthDef);
			var instr = super.newCopyArgs(synthDef, desc);
			byName[synthDef] = instr;
			^instr
		};
	}

	type { ^\synth }

	isAutoRelease { ^true }

	getDefaultValue {| param | ^synthDesc.controlDict[param] !? { |ctrl| ctrl.defaultValue }}

	create {| inst |
		var duration, asr, args, synth;
		duration = inst.def.duration !? {| dur | dur - inst.cutoff};
		args = List[duration: duration ? inf];
		inst.control_map.keysValuesDo { |name, ctrl|
		    var argument = ctrl.getSynthArgument(inst);
		    if (argument != nil) {
		        args = args.addAll([name, argument]);
		    };
		};
		//postf("Creating synth % with args %\n", synthDefName, args);
		synth = Synth.newPaused(synthDefName, args, inst.node, \addToTail);
		//synth.register(assumePlaying: true);
		synth.onFree {
		    //postf("Synth % freed, disposing instance\n", synth);
		    inst.dispose;
        };
		^synth
	}

	asString { ^"SynthDef %".format(synthDefName) }
}

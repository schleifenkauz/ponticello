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

	getDefaultValue {| param | ^synthDesc.controlDict[param] !? { |ctrl| ctrl.defaultValue }}

	create {| inst |
		var duration = inst.def.duration !? {| dur | dur - inst.cutoff}, args, synth;
		if (duration == inf || duration == nil) {
            args = List[auto_release: 0, duration: 0.1];
        } {
			args = List[duration: duration];
		};
		inst.getInitialArguments.keysValuesDo {| p, v | args = args.addAll([p, v]) };
		inst.def.controls.do { |ctrl|
		    var argument = ctrl.getSynthArgument(inst);
		    if (argument != nil) {
		        args = args.addAll([ctrl.name, argument]);
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

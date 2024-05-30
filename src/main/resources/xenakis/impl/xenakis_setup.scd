(
~setupHandler = { arg address, handler;
	OSCdef(address, {
		arg msg, time, addr, recvPort;
		var argument, id, answer;
		id = msg[1];
		answer = handler.(*msg.drop(2));
		addr.sendMsg('/reply', id, answer);
	}, address);
};
OSCdef.trace(false);
~setupHandler.('eval', { |code|
	this.interpret(code.asString).asString;
});
OSCdef('run', {
	arg msg, time, addr, recvPort;
	var code = msg[1].asString, answer;
	code.postln;
	this.interpret(code);
}, 'run');
~setupHandler.('isSynthDef', { |name|
	SynthDescLib.global.synthDescs[name.asSymbol] != nil;
});
~setupHandler.('controls', { |name|
	SynthDescLib.global.synthDescs[name.asSymbol].controls.size;
});
~setupHandler.('controlName', { |name, idx|
	SynthDescLib.global.synthDescs[name.asSymbol].controls[idx].name;
});
~setupHandler.('controlType', { |name, idx|
	var desc, ctrl, rate, spec;
	desc = SynthDescLib.global.synthDescs[name.asSymbol];
	ctrl = desc.controls[idx];
	spec = desc.specs[ctrl.name];
	if (name.asString.beginsWith("buf")) { 'buf' } { if (spec == nil) { 'bus' } { 'num' } };
});
~setupHandler.('controlDefault', { |name, idx|
	SynthDescLib.global.synthDescs[name.asSymbol].controls[idx].defaultValue;
});
~setupHandler.('hasSpec', { |synthName, controlName|
	SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName] != nil;
});
~setupHandler.('controlMinval', { |synthName, controlName|
	var minval;
	'minval of '.post; synthName.post; '.'.post; controlName.postln;
	minval = SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName].minval;
	'is '.post; minval.postln;
	minval
});
~setupHandler.('controlMaxval', { |synthName, controlName|
	SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName].maxval;
});
~setupHandler.('controlWarp', { |synthName, controlName|
	SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName].warp.asString;
});
~setupHandler.('controlStep', { |synthName, controlName|
	SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName].step;
});
"Successfully setup OSC".postln;
)


(
~setupHandler = { arg address, handler;
	OSCdef(address, {
		arg msg, time, addr, recvPort;
		var argument, id, answer;
		id = msg[2];
		argument = msg[1];
		answer = handler.(argument);
		addr.sendMsg('/reply', answer, id);
	}, address);
};
OSCdef.trace(false);
~setupHandler.('eval', { |code|
	this.interpret(code.asString);
});
OSCFunc({
	arg msg, time, addr, recvPort;
	var code = msg[1].asString, answer;
	code.postln;
	this.interpret(code);
}, 'run');
~setupHandler.('isSynthDef', { |name|
	SynthDescLib.global.synthDescs[name.asSymbol] != nil;
});
"Successfully setup OSC".postln;
)


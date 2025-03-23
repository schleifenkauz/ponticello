(
~xenakis_addr = NetAddr("localhost", 7772);

SynthDef(\vst_instrument, {
	arg out = 0;
	var snd;
	snd = VSTPlugin.ar(nil, 2);
	Out.ar(out, snd);
}).add;
SynthDef(\utility, {
    arg bus, volume, pan;
    var snd = In.ar(bus, 2);
    snd = snd * volume.dbamp;
    Out.ar(bus, snd);
}).add;
SynthDef(\send, {
    arg in, out, amp;
    var snd = In.ar(in, 2);
    snd = snd * amp;
    Out.ar(out, snd);
}).add;

ServerBoot.add {
    ~xenakis_addr.sendMsg('/booted');
	Task {
		1.wait;
		VSTPlugin.search;
	}.play;
};
ServerTree.add {
    ~xenakis_addr.sendMsg('/cleared');
};

~setupHandler = { arg address, handler;
	OSCdef(address, {
		arg msg, time, addr, recvPort;
		var id, answer;
        id = msg[1];
        try {
            answer = handler.(*msg.drop(2));
            ~xenakis_addr.sendMsg('/reply', id, answer.asString);
        } { |error|
			~xenakis_addr.sendMsg('/error', id, error.what);
        }
	}, address);
};
OSCdef.trace(false);
OSCdef('eval', {
	arg msg, time, addr, recvPort;
	var id = msg[1], code = msg[2], fct, answer;
	fct = this.compile(code.asString);
	code.postln;
    if (fct == nil) { ~xenakis_addr.sendMsg('error', id, "Compilation error") } {
        AppClock.sched(0) {
            try {
                answer = fct.value;
                ~xenakis_addr.sendMsg('/reply', id, answer.asString);
            } { |error|
                ~xenakis_addr.sendMsg('/error', id, error.what);
            }
        }
	}
}, 'eval');
OSCdef('run', {
	arg msg, time, addr, recvPort;
	var code = msg[1], fct;
	fct = this.compile(code.asString);
	if (fct == nil) { ~xenakis_addr.sendMsg('error', -1, "Compilation error") };
	AppClock.sched(0) {
		try { fct.value } { |error|
			~xenakis_addr.sendMsg('/error', -1, error.what);
		}
	}
}, 'run');
OSCdef('start_play', {
	arg msg;
	~play_start = TempoClock.beats;
	~synths = ();
	~tasks = ();
}, '/start_play');
OSCdef('pause_play', {
    ~play_start = -1;
    ~synths.do { |s| if (s.isRunning) { s.free } };
    ~tasks.do { |t| if (t.isPlaying) { t.stop; } };
}, '/pause_play');
OSCdef('schedule', {
	arg msg;
	var time = msg[2].asFloat, code = msg[3], fct, my_play_start = ~play_start;
	fct = this.compile(code.asString);
	if (fct == nil) { "Compilation error".throw };
	'SCHEDULE: '.postln;
	code.postln;
	TempoClock.schedAbs(max(~play_start + time, TempoClock.beats)) {
	    if (my_play_start == ~play_start) {
		    try { fct.value } { |error|
				~xenakis_addr.sendMsg('/error', -1, error.what);
			}
		};
		nil;
	}
}, '/schedule');
~setupHandler.('isSynthDef', { |name|
	SynthDescLib.global.synthDescs[name.asSymbol] != nil;
});
~setupHandler.('removeSynthDef', { |name|
	SynthDescLib.global.synthDescs.removeAt(name);
});
~setupHandler.('controls', { |name|
	SynthDescLib.global.synthDescs[name.asSymbol].controls.size;
});
~setupHandler.('controlName', { |name, idx|
	SynthDescLib.global.synthDescs[name.asSymbol].controls[idx].name;
});
~setupHandler.('controlDefault', { |name, idx|
	SynthDescLib.global.synthDescs[name.asSymbol].controls[idx].defaultValue;
});
~setupHandler.('hasSpec', { |synthName, controlName|
	SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName] != nil;
});
~setupHandler.('controlMinval', { |synthName, controlName|
	var minval;
	minval = SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName].minval;
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
"Successfully setup OSC!".postln;
)
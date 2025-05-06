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
}, metadata: (specs: (
    amp: [0, 1, \lin, 0.01, 1]
))).add;

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

~play_start = ();

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
			error.throw;
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
                error.throw;
            }
        }
	}
}, 'eval');
OSCdef('run', {
	arg msg, time, addr, recvPort;
	var code = msg[1], fct;
	code.postln;
	fct = this.compile(code.asString);
	if (fct == nil) { ~xenakis_addr.sendMsg('error', -1, "Compilation error") };
	AppClock.sched(0) {
		try { fct.value } { |error|
			~xenakis_addr.sendMsg('/error', -1, error.what);
			error.throw;
		}
	}
}, 'run');
OSCdef('start_play', { |msg|
    var player_id = msg[1];
    "START PLAY ".post; player_id.postln;
	~play_start[player_id] = TempoClock.beats;
}, '/start_play');
OSCdef('pause_play', { |msg|
    var player_id = msg[1];
    "PAUSE_PLAY ".post; player_id.postln;
    ~play_start[player_id] = nil;
}, '/pause_play');
OSCdef('schedule', {
	arg msg;
	var time = msg[2].asFloat, player_id = msg[3], code = msg[4], fct, my_play_start, abs_time;
	my_play_start = ~play_start[player_id];
    abs_time = (my_play_start ?? TempoClock.beats) + time;
	'SCHEDULE, player_id ='.post; player_id.post;
	', my_play_start = '.post; my_play_start.post;
	', time =  '.post; time.post;
	', abs_time = '.post; abs_time.postln;
	fct = this.compile(code.asString);
	if (fct == nil) { "Compilation error".throw };
    //code.postln;
    TempoClock.schedAbs(abs_time) {
        if (my_play_start.isNil || (my_play_start == ~play_start[player_id])) {
            try { fct.value } { |error|
                ~xenakis_addr.sendMsg('/error', -1, error.what);
                error.throw;
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
	var desc, spec;
	desc = SynthDescLib.global.synthDescs[synthName.asSymbol];
	spec = desc.specs[controlName];
	spec[0];
});
~setupHandler.('controlMaxval', { |synthName, controlName|
	SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName][1];
});
~setupHandler.('controlWarp', { |synthName, controlName|
	SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName][2];
});
~setupHandler.('controlStep', { |synthName, controlName|
	SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName][3];
});
~xenakis_addr.sendMsg('/ready');
"Successfully setup OSC!".postln;
)
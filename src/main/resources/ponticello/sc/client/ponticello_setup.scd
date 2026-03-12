(
var cond = Condition.new(true);

~ponticello_addr = NetAddr("localhost", 7775);

SynthDef(\score_clock) {
    arg start = 0, rate = 1, out;
    var clock = start + Sweep.kr(rate: In.kr(rate));
    Out.kr(out, clock);
}.add;
SynthDef(\vst_plugin, {
    arg bus = 0;
    var dry = In.ar(bus, 2), snd, mix, dry_mix = \dry.kr(0), bypass = \bypass.kr(0);
    snd = VSTPlugin.ar(dry, 2, bypass: bypass);
    snd = snd + (dry * dry_mix);
    mix = Linen.kr(\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf) * (1 - bypass);
    XOut.ar(bus, mix, snd);
}).add;
SynthDef(\utility, {
    arg bus, volume, pan;
    var snd = In.ar(bus, 2);
    snd = snd * volume.dbamp * Linen.kr(\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf);
    Out.ar(bus, snd);
}).add;
SynthDef(\send, {
    arg in, out, amp;
    var snd = In.ar(in, 2);
    snd = snd * amp * Linen.kr(\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf);
    Out.ar(out, snd);
}, metadata: (specs: (
    amp: [0, 1, \lin, 0.01, 1],
))).add;

ServerBoot.add {
    ~time_warp_bus = Bus.control(s, 1);
    ~score_time_bus = Bus.control(s, 1);
	Task {
		1.wait;
		VSTPlugin.search(verbose: false, action: { ~ponticello_addr.sendMsg('/booted') });
	}.play;
};
ServerTree.add {
    AudioNodeOrder.clear;
    ~ponticello_addr.sendMsg('/cleared');
};

~play_start = ();
~time_warp = 1;

~setupHandler = { arg address, handler;
	OSCdef(address, {
		arg msg, time, addr, recvPort;
		var id, answer;
        id = msg[1];
        try {
            answer = handler.(*msg.drop(2));
            if (id != -1) {
                ~ponticello_addr.sendMsg('/reply', id, answer.asString)
            };
        } { |error|
			~ponticello_addr.sendMsg('/error', id, error.what);
			error.throw;
        }
	}, address);
};
OSCdef.trace(false);
OSCdef('eval', {
	arg msg, time, addr, recvPort;
	var id = msg[1], code = msg[2], fct, answer;
	fct = this.compile(code.asString);
    if (fct == nil) { ~ponticello_addr.sendMsg('error', id, "Compilation error") } {
        fork {
			cond.wait;
			cond.test = false;
            try {
                answer = fct.value;
				~ponticello_addr.sendMsg('/reply', id, answer.asString);
				cond.test = true;
				cond.signal;
            } { |error|
                ~ponticello_addr.sendMsg('/error', id, error.what);
				cond.test = true;
				cond.signal;
                error.throw;
            }
        }
	}
}, 'eval');
OSCdef('run', { |msg|
	var code = msg[2], fct;
	fct = this.compile(code.asString);
	if (fct == nil) { ~ponticello_addr.sendMsg('error', "Compilation error") };
	fork {
		cond.wait;
		cond.test = false;
		try {
			fct.value;
			cond.test = true;
			cond.signal;
		} { |error|
			~ponticello_addr.sendMsg('/error', -1, error.what);
			cond.test = true;
			cond.signal;
			error.throw;
		}
	}
}, 'run');
OSCdef('start_play', { |msg|
    var player_id = msg[2].asInteger, start_time = msg[3].asFloat;
    "START_PLAY ".post; msg.postln;
	~play_start[player_id] = SystemClock.beats;
	"Start time: ".post; start_time.postln;
	~score_time_counter = Synth(\score_clock, [start: start_time, rate: ~time_warp_bus, out: ~score_time_bus]);
}, '/start_play');
OSCdef('pause_play', { |msg|
    var player_id = msg[2];
    "PAUSE_PLAY ".post; player_id.postln;
    ~play_start[player_id] = nil;
    ~score_time_counter.free;
    ~score_time_counter = nil;
}, '/pause_play');
OSCdef('schedule', {
	arg msg;
	var id = msg[1], absolute = msg[2], time = msg[3].asFloat, player_id = msg[4], code = msg[5], info = msg[6], fct, my_play_start, abs_time;
	my_play_start = ~play_start[player_id];
    abs_time = if (absolute) { time } { time + (my_play_start ? SystemClock.beats) };
    //postf("SCHEDULE %: player_id = %, my_play_start = %, time = %, abs_time = %, current time = %\n", info, player_id, my_play_start, time, abs_time, TempoClock.beats);
	fct = this.compile(code.asString);
	if (fct == nil) { "Compilation error".throw };
    SystemClock.schedAbs(abs_time) {
        if (my_play_start.isNil || (my_play_start == ~play_start[player_id])) {
            fork {
                //postf("EXECUTE %\n", info);
                //code.postln;
                try {
                    var answer = fct.value(player_id);
                    ~ponticello_addr.sendMsg('/reply', id, answer.asString);
                } { |error|
                    ~ponticello_addr.sendMsg('/error', id, error.what);
                    error.throw;
                }
            }
        } {
            postf("Rejecting % scheduled for % because % != %\n", info, abs_time, my_play_start, ~play_start[player_id]);
        };
        nil;
    }
}, '/schedule');
OSCdef(\save_plugin_state, { |msg|
    var id = msg[1], controller_var = msg[2], path = msg[3], controller;
    controller = currentEnvironment[controller_var.asSymbol];
    if (controller != nil) {
        try {
            controller.writeProgram(path.asString) { |ctrl, ok|
                ~ponticello_addr.sendMsg('/reply', id, ok.asString)
            }
        } { |error|
            ~ponticello_addr.sendMsg('/error', id, error.what);
            error.throw;
        }
    } { ~ponticello_addr.sendMsg('/reply', id, "false") }
}, \save_plugin_state);
OSCdef(\set_time_warp, { |msg|
    var tempo = msg[2].asFloat;
    if (msg[3] != nil) {
        var score_time = msg[3].asFloat;
        if (score_time > 1) {
            var diff = score_time - ~score_time_bus.getSynchronous;
            //postf("exp(tanh(% - %)) = %\n", score_time, ~score_time_bus.getSynchronous, diff.tanh.exp);
            tempo = tempo * (diff.tanh * 0.3).exp;
        }
    };
    if (tempo > 0) {
        TempoClock.tempo = tempo;
        ~time_warp_bus.set(max(tempo, 0.1));
        ~time_warp = tempo;
        "ok"
    } { "non-positive tempo" }
}, '/set_time_warp');

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
	SynthDescLib.global.synthDescs[synthName.asSymbol].specs[controlName][0];
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

~addLevelSendSynthDef = { |channels|
    SynthDef("level_send_" ++ channels.asString) {
        arg bus, id;
        var sig = In.ar(bus, channels), levels;

        levels = Amplitude.kr(sig, 0.01, 0.3).ampdb;
        levels = levels.lag(\lag.kr(0.05));

        FreeSelf.kr(1 - \gate.kr(1));

        SendReply.kr(Impulse.kr(\rate.kr(10)), '/bus_levels', levels, replyID: id)
    }.add;
};
OSCdef(\bus_levels, { arg msg;
    ~ponticello_addr.sendMsg('/bus_levels', *msg[2..]);
}, '/bus_levels');
~ponticello_addr.postln;
~ponticello_addr.sendMsg('/ready');
"Successfully setup OSC!";
)
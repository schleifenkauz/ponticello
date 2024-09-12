(
SynthDef(\vst_instrument, {
	arg out = 0;
	var snd;
	snd = VSTPlugin.ar(nil, 2);
	Out.ar(out, snd);
}).add;
SynthDef(\diskout, {
	arg bus = 0, buf;
	DiskOut.ar(buf, In.ar(bus, 2));
}).add;
SynthDef(\playbuf, {
    var buf, amp, rate, startPos, out, snd, duration;
    buf = \buf.kr;
    amp = \amp.kr(0.1, spec: [0.0, 1.0, 'lin', 0.01]);
    rate = \rate.kr(1.0, spec: [0.01, 5.0, 'lin', 0.01]);
    startPos = \startPos.kr(0.0, spec: [0.0, 10000.0, 'lin', 0.01]);
    out = \out.kr;
    duration = \duration.ir;
    snd = PlayBuf.ar(2, buf, rate: (BufRateScale.kr(buf) * rate), startPos: (startPos * BufSampleRate.kr(buf)), loop:  1);
    snd = (snd * amp);
    Out.ar(out, snd);
    Env.new(levels: [0, 0], times: [duration]).kr(Done.freeSelf);
  }).add;

~record_buf = Buffer.alloc(s, 65536, 2, );
~tmp_path = "~/.xenakis/tmp.wav".standardizePath;
'recording to '.post; ~tmp_path.postln;
~record_buf.write(~tmp_path, "wav", "int16", 0, 0, true);

NetAddr.langPort.postln;
NetAddr.localAddr.postln;

~startRecording = { arg bus = 0;
	~record_synth = Synth.tail(nil, \diskout, [bus:bus, buf:~record_buf]);
};

ServerBoot.add {
	Task {
		1.wait;
		VSTPlugin.search;
	}.play;
};

~play = false;

~setupHandler = { arg address, handler;
	OSCdef(address, {
		arg msg, time, addr, recvPort;
		var id, answer;
		AppClock.sched(0.0) {
			id = msg[1];
			answer = handler.(*msg.drop(2));
			'sending reply: '.post;
			addr.postln;
			addr.sendMsg('/reply', id, answer);
		}
	}, address);
};
OSCdef.trace(false);
OSCdef('writeBuf', {
    arg msg, time, addr, recvPort;
    var bufnum, filename, id, answer;
    AppClock.sched(0.0) {
        id = msg[1];
        bufnum = msg[2];
        filename = msg[3];
        Buffer.cachedBufferAt(bufnum).write(filename, 'wav', 'int16') {
            addr.sendMsg('/replay', id, 'ok');
        }
    }
});
~setupHandler.('eval', { |code|
	var answer;
	'eval: '.post;
	code.asString.postln;
	answer = this.interpret(code.asString).asString;
	answer;
});
OSCdef('run', {
	arg msg, time, addr, recvPort;
	var code = msg[1].asString, answer;
	'eval: '.post;
    code.postln;
	AppClock.sched(0, {
		this.interpret(code);
	});
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
    'PAUSE'.postln;
}, '/pause_play');
OSCdef('schedule', {
	arg msg;
	var time = msg[2].asFloat, code = msg[3].asString, my_play_start = ~play_start;
	TempoClock.schedAbs(max(~play_start + time, TempoClock.beats)) {
	    if (my_play_start == ~play_start) {
		    this.interpret(code);
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
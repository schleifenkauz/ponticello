(
~ponticello_addr = NetAddr("localhost", 7775);

SynthDef(\vst_instrument, {
	arg out = 0;
	var snd;
	snd = VSTPlugin.ar(nil, 2);
	snd = snd * Linen.kr(\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf);
	Out.ar(out, snd);
}).add;
SynthDef(\vst_plugin, {
    arg bus = 0;
    var snd = In.ar(bus, 2), mix;
    snd = VSTPlugin.ar(snd, 2, bypass: \bypass.kr(0));
    mix = Linen.kr(\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf);
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
    ~ponticello_addr.sendMsg('/booted');
    ~time_warp_bus = Bus.control(s, 1);
	Task {
		1.wait;
		VSTPlugin.search;
	}.play;
};
ServerTree.add {
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
            ~ponticello_addr.sendMsg('/reply', id, answer.asString);
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
            try {
	            //code.postln;
                answer = fct.value;
                ~ponticello_addr.sendMsg('/reply', id, answer.asString);
            } { |error|
                ~ponticello_addr.sendMsg('/error', id, error.what);
                error.throw;
            }
        }
	}
}, 'eval');
OSCdef('run', {
	arg msg, time, addr, recvPort;
	var code = msg[2], fct;
	fct = this.compile(code.asString);
	if (fct == nil) { ~ponticello_addr.sendMsg('error', "Compilation error") };
	fork {
	    //code.postln;
		try { fct.value } { |error|
			~ponticello_addr.sendMsg('/error', nil, error.what);
			error.throw;
		}
	}
}, 'run');
OSCdef('start_play', { |msg|
    var player_id = msg[2];
    "START PLAY ".post; msg.postln;
	~play_start[player_id] = TempoClock.beats;
}, '/start_play');
OSCdef('pause_play', { |msg|
    var player_id = msg[2];
    "PAUSE_PLAY ".post; player_id.postln;
    ~play_start[player_id] = nil;
}, '/pause_play');
OSCdef('schedule', {
	arg msg;
	var time = msg[2].asFloat, player_id = msg[3], code = msg[4], info = msg[5], fct, my_play_start, abs_time;
	my_play_start = ~play_start[player_id];
    abs_time = (my_play_start ?? TempoClock.beats) + time;
    postf("SCHEDULE %: player_id = %, my_play_start = %, time = %, abs_time = \n", info, player_id, my_play_start, time, abs_time);
	fct = this.compile(code.asString);
	if (fct == nil) { "Compilation error".throw };
    TempoClock.schedAbs(abs_time) {
        if (my_play_start.isNil || (my_play_start == ~play_start[player_id])) {
            fork {
                postf("EXECUTE %\n", info);
                code.postln;
                try { fct.value } { |error|
                    ~ponticello_addr.sendMsg('/error', error.what);
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
        controller.writeProgram(path.asString) { |ctrl, ok|
            ~ponticello_addr.sendMsg('/reply', id, ok.asString)
        }
    } { ~ponticello_addr.sendMsg('/reply', id, "false") }
}, \save_plugin_state);
OSCdef(\set_time_warp, { |msg|
    var tempo = msg[2].asFloat;
    TempoClock.tempo = tempo;
    ~time_warp_bus.set(tempo);
    ~time_warp = tempo;
    postf("Time warp: %\n", tempo);
    "ok"
}, '/set_time_warp');

~setupHandler.(\startCircularBuffer, { |duration|
    var frames;
    ~circularRecorder.free;
    ~circularBuffer.free;
    frames = duration.asFloat * s.sampleRate;
    ~circularBuffer = Buffer.alloc(s, frames, 2);
    ~circularRecorder = SynthDef(\circularRecord, {
        arg buf;
        var in, writePos, trig;
        in = SoundIn.ar([0, 1]);
        writePos = Phasor.ar(0, 1, 0, frames);
        BufWr.ar(in, ~circularBuffer.bufnum, writePos, loop: 1);
    }).play;
    ~t_start_recording = AppClock.seconds;
    postf("Start recording to circular buffer of duration % s\n", duration);
    "ok"
});

~setupHandler.(\markBufferSegmentEnd, {
    if (~circularBuffer != nil) {
        ~buffer_segment_end = ((AppClock.seconds - ~t_start_recording) % ~circularBuffer.duration);
        postf("Marking buffer segment end: %\n", ~buffer_segment_end);
    } {
        "ERROR: Recording to circular buffer was not started.".postln;
    }
});

~setupHandler.(\exportBufferSegment, { |path, duration|
    var numFrames, startFrame;
    if (~circularBuffer != nil) {
        numFrames = duration.asFloat * s.sampleRate;
        startFrame = ~buffer_segment_end * s.sampleRate - numFrames;
        if (startFrame < 0) { startFrame = startFrame + ~circularBuffer.numFrames };
        postf("Writing % seconds to % starting from %\n", duration, path, startFrame / s.sampleRate);
        ~circularBuffer.write(path, "wav", "int16", numFrames, startFrame, false) {
            postf("Finished writing to %\n", path);
            ~ponticello_addr.sendMsg('/completed', 0, path);
        };
        "ok"
    } {
        "ERROR: Recording to circular buffer was not started.".postln;
    }
}, \exportBufferSegment);

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
~ponticello_addr.postln;
~ponticello_addr.sendMsg('/ready');
"Successfully setup OSC!";
)
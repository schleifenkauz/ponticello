PonticelloPlayback {
	classvar play_start, score_time_counter, score_time_bus, time_warp_bus, time_warp;

	* init {
		play_start = Dictionary.new;
		score_time_bus = Bus.control(Server.local, 1);
		time_warp_bus = Bus.control(Server.local, 1);
		time_warp = 1;
	}

	* start_play { |player_id, start_time|
		player_id = player_id.asFloat; start_time = start_time.asFloat;
		play_start[player_id] = SystemClock.beats;
		score_time_counter = Synth(\score_clock, [start: start_time, rate: time_warp_bus, out: score_time_bus]);
		^nil
	}

	* pause_play { |player_id|
		play_start.removeAt(player_id);
		score_time_counter.free;
		score_time_counter = nil;
	}

	* set_time_warp { |tempo, score_time|
		tempo = tempo.asFloat; score_time = score_time.asFloat;
		if (score_time.notNil) {
			if (score_time > 1) {
				var diff = score_time - score_time_bus.getSynchronous;
				//postf("exp(tanh(% - %)) = %\n", score_time, ~score_time_bus.getSynchronous, diff.tanh.exp);
				tempo = tempo * (diff.tanh * 0.3).exp;
			}
		};
		^if (tempo > 0) {
			TempoClock.tempo = tempo;
			time_warp_bus.set(max(tempo, 0.1));
			time_warp = tempo;
			"ok"
		} { "non-positive tempo" }
	}

	* schedule { |id, absolute, time, player_id, code, info|
		var fct, my_play_start, abs_time;
		my_play_start = play_start[player_id];
		abs_time = if (absolute) { time } { time + (my_play_start ? SystemClock.beats) };
		fct = thisProcess.interpreter.compile(code.asString);
		if (fct == nil) {
			Ponticello.sendMsg('/error', id, "Compilation error!");
		} {
			SystemClock.schedAbs(abs_time) {
				if (my_play_start.isNil || (my_play_start == play_start[player_id])) {
					Ponticello.attempt(id) {
						var answer = fct.value(player_id);
						Ponticello.sendMsg('/reply', id, answer.asString);
					}
				} {
					postf("Rejecting % scheduled for % because % != %\n",
						info, abs_time, my_play_start, play_start[player_id]);
				};
				nil;
			}
		}
	}

	* sweep {
		^Sweep.kr(rate: time_warp_bus.kr)
	}

	* freeAfter { |duration|
		var env = Env.step([0, 1], [duration, 1]);
		var gate_env = IEnvGen.kr(env, index: this.sweep) * \auto_release.kr(1);
		^FreeSelf.kr(gate_env + (1 - \gate.kr(1)))
	}

	 //TODO does this react to updates to the \duration control?
	* asrEnv { |duration, attack, release|
		var sustain = duration - (attack + release);
		var env = Env([1, 1, 1 - \auto_release.kr(1)], [(attack + sustain).max(0.002), 0]);
		var gate_env = IEnvGen.kr(env, this.sweep);
		^Env.asr(attack, 1, release).kr(Done.freeSelf, gate_env * \gate.kr(1), timeScale: time_warp_bus.kr);
	}
}
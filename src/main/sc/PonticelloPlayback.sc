PonticelloPlayback {
	classvar play_start, score_time_counter, score_time_bus, time_warp_bus, time_warp;

	* init {
		play_start = Dictionary.new;
		score_time_bus = Bus.control(Server.local, 1);
		time_warp_bus = Bus.control(Server.local, 1);
		time_warp_bus.set(1);
		time_warp = 1;
	}

	* start_play { |player_id, score_start_time, play_start_time|
		score_start_time = score_start_time.asFloat;
		play_start_time = play_start_time ?? { SystemClock.seconds };
		postf("Start Play: %, score_start_time: %, play_start_time: %\n", player_id, score_start_time, play_start_time);
		play_start.put(player_id.asInteger, play_start_time);
		score_time_counter = Synth(\score_clock, [start: score_start_time, rate: time_warp_bus, out: score_time_bus]);
	}

	* pause_play { |player_id|
		play_start.removeAt(player_id);
		score_time_counter.free;
		score_time_counter = nil;
		SoundProcess.stopAllProcesses(player_id);
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
		time = time.asFloat;
		abs_time = if (absolute) { time } {
			var reference = my_play_start ? SystemClock.seconds;
			time + reference
		};
		fct = thisProcess.interpreter.compile(code.asString);
		if (fct == nil) {
			Ponticello.sendMsg('/error', id, "Compilation error!");
		} {
			SystemClock.schedAbs(abs_time) {
				if (my_play_start.isNil || (my_play_start == play_start[player_id])) {
					fork {
						Ponticello.attempt(id) {
							var answer = fct.value(player_id);
							Ponticello.sendMsg('/reply', id, answer.asString);
						}
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
		FreeSelf.kr(this.sweep > duration);
		^FreeSelf.kr(1 - \gate.kr(1))
	}

	* asrEnv { |duration, attack, release, curve=\linear|
		var auto_release = this.sweep < (duration - release);
		var env = Env.asr(attack, 1, release, curve);
		var gate = auto_release * \gate.kr(1);
		^env.kr(Done.freeSelf, gate, timeScale: time_warp_bus.kr);
	}
}
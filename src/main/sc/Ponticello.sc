Ponticello {
	classvar ponticello_addr, interpreter;

	* attempt { |id, action|
		action.try { |error|
			Ponticello.sendMsg('/error', id, error.what);
			error.reportError;
		}
	}

	* respond { |address, handler|
		^OSCdef(address, { arg msg;
			fork {
				Ponticello.attempt(-1) {
					handler.value(*msg.drop(1));
				}
			}
		}, address);
	}

	* respondId { |address, handler|
		^OSCdef(address, { arg msg;
			fork {
				handler.value(*msg.drop(1));
			}
		}, address);
	}

	* respondResult { |address, handler|
		^OSCdef(address, { arg msg;
			fork {
				var id = msg[1];
				Ponticello.attempt(id) {
					var result = handler.value(*msg.drop(2));
					Ponticello.sendMsg('/reply', id, result);
				}
			}
		}, address);
	}

	* sendMsg { |... args|
		ponticello_addr.sendMsg(*args);
	}

	* eval { |id, code|
		var fct = thisProcess.interpreter.compile(code.asString);
		if (fct == nil) {
			Ponticello.sendMsg('/error', id, "Compilation error");
		} {
			Ponticello.attempt(id) {
				var answer = fct.value;
				Ponticello.sendMsg('/reply', id, answer.asString);
			}
		}
    }

	* run { |code|
		var fct = thisProcess.interpreter.compile(code.asString);
		if (fct == nil) {
			Ponticello.sendMsg('/error', -1, "Compilation error");
		} {
			Ponticello.attempt(id: -1, action: fct)
		}
	}

	* save_plugin_state { |id, controller_var, path|
		var controller = currentEnvironment[controller_var.asSymbol];
		if (controller != nil) {
			controller.writeProgram(path.asString) { |ctrl, ok|
                Ponticello.sendMsg('/reply', id, ok.asString)
            }
		};
		^nil
	}

	* doOnStartUp {
		ServerTree.add {
			AudioNodeOrder.clear;
			Ponticello.sendMsg('/cleared');
		};
		ServerBoot.add {
			PonticelloPlayback.init;
			fork {
				1.wait;
				VSTPlugin.search(verbose: false, action: { Ponticello.sendMsg('/booted') });
			};
		};

		DefaultSynthDefs.addAll;

		Ponticello.respond('/save_plugin_state', this.save_plugin_state(_, _, _));
		Ponticello.respond('/run', this.run(_));
		Ponticello.respondId('/eval', this.eval(_, _));
		Ponticello.respondId('/schedule', PonticelloPlayback.schedule(_, _, _, _, _, _));
		Ponticello.respond('/set_time_warp', PonticelloPlayback.set_time_warp(_, _));
		Ponticello.respond('/start_play', PonticelloPlayback.start_play(_, _));
		Ponticello.respond('/pause_play', PonticelloPlayback.pause_play(_));

		postf("Successfully setup OSC handling. Replying to \n", ponticello_addr);
		Ponticello.sendMsg('/ready');
	}

	* initClass {
		ponticello_addr = NetAddr("localhost", 7775);
		StartUp.add(this);
	}
}
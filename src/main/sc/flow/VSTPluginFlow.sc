VSTPluginFlow : AudioFlow {
	var pluginName, preset, <bus, <controller;

	* new { |name, pluginName, preset, bus|
		^super.newCopyArgs(name, pluginName, preset, bus)
	}

	createNode { |target, addAction|
		var synth, dry;
		dry = (VSTPlugin.plugins[pluginName].inputs != nil).asInteger;
		synth = this.prCreateSynth(\vst_plugin, [bus: bus, dry: dry], target, addAction);
		Server.local.sync;
		controller = VSTPluginController(synth);
		controller.open(pluginName, editor: true, multiThreading: true, action: { |controller|
			if (preset.notNil) {
				controller.loadPreset(preset);
			} {
				var plugin_state_file = this.pluginStateFile;
				if (File.exists(plugin_state_file)) {
					controller.readProgram(plugin_state_file);
				}
			};
		});
		^synth;
	}

	bus_ { |value|
		bus = value;
		node.set(\bus, value);
	}

	pluginStateFile {
		var plugin_states_dir = Ponticello.project_directory +/+ "plugin_states";
		File.mkdir(plugin_states_dir);
		^plugin_states_dir +/+ name ++ ".fxp"
	}

	automatableParameters {
		var parameters = controller.info.parameters, str = "";
		parameters.do { |p| if (p.automatable) { str = str ++ "," ++ p.name } };
		^str
	}

	active_ { |enable, notify|
		node.set(\bypass, enable.not);
		super.active_(enable, notify);
	}

	* savePluginState { |reply_id, flow_name|
		var flow = AudioFlow.get(flow_name);
		if (flow.isNil || {flow.controller.isNil}) {
			Ponticello.sendMsg('/error', reply_id, "Could not find VSTPluginController for flow %".format(flow_name));
		};
		flow.controller.writeProgram(flow.pluginStateFile) { |ctrl, ok|
			if (ok) {
				Ponticello.sendMsg('/reply', reply_id, "ok");
			} {
				Ponticello.sendMsg('/error', reply_id, "Error saving VST plugin state of flow %".format(flow_name));
			}
		}
	}

	apply { |func| func.value(this); }
}
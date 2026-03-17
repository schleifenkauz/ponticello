+ SoundProcessInstance {
	pitch { |default| ^this.get('pitch') ? default }
	velocity { |default| ^this.get('velocity') ? default }
    chan { |default| ^this.get('channel') ? default }
    midiSource {
        ^(
			track: this.midi_track, chan: this.chan(default: 0),
			server_latency: this.server_latency, player_id: this.player_id,
			controls: this.control_map.values
		)
    }
}

+ VSTPlugin {
    * pluginListString { |midi=false|
        var str = "";
        VSTPlugin.pluginList.do { |desc|
            if (midi.not || desc.midiInput) {
                str = str ++ desc.key ++ ",";
            }
        }
        ^str;
    }
}
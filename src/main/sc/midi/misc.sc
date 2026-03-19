+ SoundProcessInstance {
	pitch { |default| ^this.get('pitch') ? default }
	freq { |default| ^this.pitch(default).midicps }
	velocity { |default, min=0, max=127, curve=\lin|
	    var velo = this.get('velocity') ? default;
	    ^velo.lincurve(min, max, curve);
    }
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
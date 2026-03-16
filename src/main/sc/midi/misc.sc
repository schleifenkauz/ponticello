+ SoundProcessInstance {
	pitch { |default| ^this.get('pitch') ? default }
	velocity { |default| ^this.get('velocity') ? default }
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
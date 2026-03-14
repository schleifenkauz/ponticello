+ SoundProcessInstance {
	pitch { ^this.get('pitch') }
	velocity { ^this.get('velocity') }
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
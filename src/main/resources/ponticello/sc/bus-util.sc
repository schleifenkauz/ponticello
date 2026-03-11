+ OutputProxy {
    bus { |rate, channels, s| ^Bus.new(rate, this, channels, s ? Server.local) }

    audioBus { |channels, s| ^this.bus(\audio, channels, s) }

    controlBus { |channels, s| ^this.bus(\control, channels, s) }
}

+ Bus {
    scan { |offset, channels|
        offset = offset ? 0;
        channels = channels ? this.numChannels;
        ^this.rate.switch
            {\audio} { In.ar(this.index + offset, channels) }
            {\control} { In.kr(this.index + offset, channels) };
    }

    out { |sig|
        ^this.rate.switch
            {\audio} { Out.ar(this, sig) }
            {\control} { Out.kr(this, sig) };
    }

    replace { |sig, mix = 1|
        ^if (mix == 1)
            { this.rate.switch
                  {\audio} { ReplaceOut.ar(this, sig) }
                  {\control} { ReplaceOut.kr(this, sig) } }
            { this.rate.switch
                  {\audio} { XOut.ar(this, mix, sig) }
                  {\control} { XOut.kr(this, mix, sig) } }

    }

    transformSignal { |mix=1, fn|
        var sig = this.scan;
        sig = fn.value(sig);
        ^this.replace(sig, mix)
    }
}
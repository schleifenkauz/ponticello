Meter {
	var <>beatsPerMinute, <>beatsPerBar, <>ticksPerBeat;

	* new { |bpm, bpb, tpb|
		if (bpm <= 0) { "Invalid BPM: %".format(bpm).throw };
		if (bpb <= 0 || bpb.round != bpb) { "Invalid beats per bar: %".format(bpb).throw };
		if (tpb <= 0 || tpb.round != tpb) { "Invalid ticks per bar: %".format(tpb).throw };
		^Meter.newCopyArgs(bpm, bpb, tpb);
	}

	beatDur { ^60 / beatsPerMinute }

	barDur { ^this.beatDur * beatsPerBar }

	tickDur { ^this.beatDur / ticksPerBeat }

	beatRate { ^beatsPerMinute / 60 * TempoClock.tempo }

	barRate { ^this.beatRate / beatsPerBar }

	tickRate { ^this.beatRate / ticksPerBeat }

	asString { ^"Meter: % bpm, %x%".format(beatsPerMinute, beatsPerBar, ticksPerBeat) }
}

+Number {
	beats { |meter| ^this * meter.beatDur }
	ticks { |meter| ^this * meter.tickDur }
	bars { |meter| ^this * meter.barDur }
}

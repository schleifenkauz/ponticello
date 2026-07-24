+ DUGen {
	kr { |dur, reset=0, doneAction=0| ^Duty.kr(dur, reset, this, doneAction) }

	ar { |dur, reset=0, doneAction=0| ^Duty.ar(dur, reset, this, doneAction) }

	krTrig { |trig, reset=0, doneAction=0| ^Demand.kr(trig, reset, this) }

	arTrig { |trig, reset=0, doneAction=0| ^Demand.ar(trig, reset, this) }
}

+ UGen {
	avg { |time, max_time|
		var num_samp = ControlRate.ir * time;
		var max_samp = ControlRate.ir * (max_time ? time);
		^MovingAverage.kr(this, num_samp, max_samp)
	}

	delay { |time, max_time|
		^switch(this.rate)
		{ \control } { DelayL.kr(this, max_time ? time, time) }
		{ \audio } { DelayL.ar(this, max_time ? time, time) }
		{ Error("% has invalid UGen rate % for .delay".format(this, this.rate)).throw }
	}
}
(
s.waitForBoot {
	b = Buffer.read(s, "/home/nikolaus/compositions/trio/samples/write_on_metal.wav")
}
)

(
SynthDef(\granular_spectral_centroid) {
	arg buf, pos, amp = 1;
	var snd, chain, pcile, centroid, amplitude, loud, send_trig;
	snd = GrainBuf.ar(1, Impulse.kr(200), 0.05, buf, pos: pos + LFNoise0.ar(200, 0.001));
	snd = snd * amp;
	chain = FFT(LocalBuf.new(4096), snd);
	pcile = SpecPcile.kr(chain, \fraction.kr(0.5), interpolate: 1).poll;
	centroid = SpecCentroid.kr(chain);
	loud = Loudness.kr(chain);
	amplitude = Amplitude.kr(snd);
	send_trig = DelayN.kr(HPZ1.kr(pos).abs > 0, 0.2, 0.2);
	SendReply.kr(send_trig, '/centroid', [pos, pcile, centroid, amplitude, loud]);

	Out.ar(0, snd ! 2);
}.add
)

(
OSCdef(\centroid_receiver, { |msg|
	var address, nodeId, reply_id, pos, pcile, centroid, amp, loud;
	#address, nodeId, reply_id, pos, pcile, centroid, amp, loud = msg;
	postf("% -> %\n", pos, centroid);
	if (~centroids.size < n) {
		~centroids.add(centroid);
		~pciles.add(pcile);
		~amps.add(amp);
		~loudness.add(loud);
	}
}, '/centroid').fix;
)

(
OSCdef(\centroid_receiver).enable;
~centroids = List[];
~pciles = List[];
~amps = List[];
~loudness = List[];
n = 200;
{
	x = Synth(\granular_spectral_centroid, [buf: b, pos: 1]);
	1.wait;
	n.do { |i|
		x.set(\pos, i / n);
		0.3.wait;
	};
	1.wait;
	x.free;
}.fork
)

[~centroids, ~pciles].asArray.plot

[[0, 1, 2], [2, 0, 2]].plot

(
~lisWithFactorSeq = { |arr, factor = 1.0|
    var n = arr.size;
    var dp = Array.fill(n, 1);
    var prev = Array.fill(n, -1);
    var bestEnd = 0;
	var seq, k;

    n.do { |i|
        i.do { |j|
            if(arr[i] >= (arr[j] * factor) and: { (dp[j] + 1) > dp[i] }) {
                dp[i] = dp[j] + 1;
                prev[i] = j;
            };
        };

        if(dp[i] > dp[bestEnd]) {
            bestEnd = i;
        };
    };

    // reconstruct
	seq = List.new; k = bestEnd;


    while { k >= 0 } {
        seq.addFirst(k -> arr[k]);
        k = prev[k];
    };

    seq;
};
)

(
~lis = ~lisWithFactorSeq.(~pciles, 1.14);
~lis.size
)

~pciles.asArray.plot

(
{
	x = Synth(\granular_spectral_centroid, [buf: b, pos: ~lis[0]]);
	(150..189).do { |p|
		x.set(\pos, p / n);
		1.wait;
	};
	x.free;
}.fork
)

~lis.collect(_.key / n)

~amp.size

~lis.do(_.postln)
~arr.do { |v, p| postf("% -> %\n", p, v) }
(~lis.size - 1).do { |i| (~lis[i+1].value / ~lis[i].value).postln }

~lis.size

[1, 2, 3].at(0.49.round)

/*
(5 -> 2794.2026367188)
(6 -> 3833.5021972656)
(21 -> 4194.3505859375)
(59 -> 4634.173828125)
(68 -> 5035.919921875)
(90 -> 5517.1274414062)
(91 -> 5979.82421875)
(92 -> 6688.39453125)
(96 -> 8263.2705078125)
(97 -> 9189.21875)
*/

//0.04, 0.34, 0.43, 0.44, 0.515, 0.575, 0.6, 0.62, 0.66, 0.8, 0.815, 0.94

/*
(8 -> 768.98303222656)
(68 -> 1161.6345214844)
(86 -> 1367.5616455078)
(88 -> 1682.0584716797)
(103 -> 1922.3428955078)
(115 -> 2197.5834960938)
(120 -> 2649.400390625)
(124 -> 3426.0478515625)
(132 -> 4050.7329101562)
(160 -> 4821.7172851562)
(163 -> 5617.2250976562)
(188 -> 7215.5771484375)
*/
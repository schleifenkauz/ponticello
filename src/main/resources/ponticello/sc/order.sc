AudioNode {
	score_y {}

	node {  }
}

SimpleAudioNode {
	var <>node, <>score_y;
}

AudioNodeOrder {
	classvar nodes;

	* binarySearch { |score_y|
		var low = 0, high = nodes.size - 1, mid;

		while { low < high } {
			mid = ((low + high) / 2).asInteger;

			if (nodes[mid].score_y < score_y) {
				low = mid + 1;
			} {
				high = mid;
			}
		};
		^low;
	}

	* initClass {
		nodes = [];
	}

	* insert { |node|
		var idx = AudioNodeOrder.binarySearch(node.score_y);
		nodes = nodes.insert(idx, node);
		^if (idx == 0) {
			(target: Server.local.defaultGroup, addAction: \addToHead);
		} {
			(target: nodes[idx - 1].node, addAction: \addAfter);
		}
	}

	* insertSynth { |score_y, def, args|
		var node = SimpleAudioNode.new.score_y_(score_y);
		var placement = this.insert(node);
		var synth = Synth(def, args, placement.target, placement.addAction);
		node.node_(synth);
		^synth;
	}

	* insertAdhocSynth { |score_y, out, graphFunc|
		var node = SimpleAudioNode.new.score_y_(score_y);
		var placement = this.insert(node);
		var synth = graphFunc.play(placement.target, out, addAction: placement.addAction);
		node.node_(synth);
		^synth;
	}

	* insertGroup { |score_y|
		var node = SimpleAudioNode.new.score_y_(score_y);
		var placement = this.insert(node);
		var group = Group.new(placement.target, placement.addAction);
		node.node = group;
		^node
	}

	moved { |node|
		var new_y = node.score_y;
		var old_idx = nodes.indexOf(node);
		var below_prev = (old_idx == 0) || (nodes[old_idx - 1].score_y < new_y);
		var above_next = (old_idx == nodes.size - 1) || (nodes[old_idx + 1].score_y > new_y);
		if (below_prev.not || above_next.not) {
			var new_idx;
			nodes.removeAt(old_idx);
			new_idx = this.binarySearch(new_y);
			nodes = nodes.insert(new_idx, node);
			if (new_idx != old_idx) {
				if (new_idx == 0) {
					node.node.moveToHead(Server.local.defaultGroup);
				} {
					var prev = nodes[new_idx - 1];
					node.node.moveAfter(prev.node);
				}
			}
		}
	}

	* remove { |node|
		nodes.remove(node);
	}

	* clear {
		nodes = [];
	}
}
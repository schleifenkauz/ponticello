AudioNode {
	score_y {}

	node {  }

	group { ^this.node }

	asTarget { ^this.node }

	moveToHead { |target| this.asTarget.moveToHead(target.asTarget) }

	moveAfter { |node| this.asTarget.moveAfter(node.asTarget) }

	moveBefore { |node| this.asTarget.moveBefore(node.asTarget) }
}

SimpleAudioNode : AudioNode {
	var <>score_y, <>node;

	* new { |score_y, node|
		^SimpleAudioNode.newCopyArgs(score_y, node);
	}

	isActive { ^true }
}

AudioNodeOrder {
	classvar nodes;

	* binarySearch { |score_y|
		var low = 0, high = nodes.size, mid;

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
		//postf("Inserting node (score_y = %) at index %\n", node.score_y, idx);
		nodes = nodes.insert(idx, node);
		idx = idx - 1;
		while { (nodes[idx] != nil) && { (nodes[idx].node == nil) || { nodes[idx].isActive.not } } } {
            nodes.removeAt(idx);
		    idx = idx - 1;
		};
		^if (idx < 0) {
			(target: Server.local.defaultGroup, addAction: \addToHead);
		} {
			(target: nodes[idx].node, addAction: \addAfter);
		}
	}

	* insertSynth { |score_y, def, args|
		var node = SimpleAudioNode.new(score_y);
		var placement = this.insert(node);
		var synth = Synth(def, args, placement.target, placement.addAction);
		node.node_(synth);
		^synth;
	}

	* insertAdhocSynth { |score_y, out, graphFunc|
		var node = SimpleAudioNode.new(score_y);
		var placement = this.insert(node);
		var synth = graphFunc.play(placement.target, out, addAction: placement.addAction);
		node.node_(synth);
		^synth;
	}

	* insertGroup { |score_y|
		var node = SimpleAudioNode.new(score_y);
		var placement = this.insert(node);
		var group = Group.new(placement.target, placement.addAction);
		node.node = group;
		^node
	}

	* move { |node, new_y|
		var old_idx = nodes.indexOf(node);
		var new_idx = this.binarySearch(new_y);
		if (new_idx != old_idx) {
		    nodes.removeAt(old_idx);
		    if (old_idx < new_idx) { new_idx = new_idx - 1 };
		    nodes = nodes.insert(new_idx, node);
		    if (new_idx == 0) {
                node.moveToHead(Server.local.defaultGroup);
            } {
                var prev = nodes[new_idx - 1];
                node.moveAfter(prev.node);
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
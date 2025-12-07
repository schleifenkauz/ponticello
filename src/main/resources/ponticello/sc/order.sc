AudioNode {
	score_y {}

	node {  }
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
		nodes.insert(idx, node);
		^if (idx == 0) {
			(target: Server.local.defaultGroup, addAction: \addToHead);
		} {
			(target: nodes[idx - 1].node, addAction: \addAfter);
		}
	}

	* remove { |node|
		nodes.remove(node);
	}

	* clear {
		nodes = [];
	}
}
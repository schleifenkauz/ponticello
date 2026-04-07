AudioNodeOrder {
	classvar nodes, idCounter = 0;

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
		nodes = List[];
	}

	* insert { |node, done|
		var idx = AudioNodeOrder.binarySearch(node.score_y);
		var prev = idx - 1;
		//postf("Inserting node (score_y = %) at index %\n", node.score_y, idx);
		nodes = nodes.insert(idx, node);
		while { (nodes[prev] != nil) && { (nodes[prev].node == nil) || { nodes[prev].isActive.not } } } {
            nodes.removeAt(prev);
		    prev = prev - 1;
		};
		^if (prev < 0) {
			(target: Server.local.defaultGroup, addAction: \addToHead);
		} {
			(target: nodes[prev].node, addAction: \addAfter);
		};
	}

	* insertFlowGroup { |score_y, name|
		var node = AudioFlowGroup.new(score_y);
		var placement = this.insert(node);
		var group = Group.new(placement.target, placement.addAction);
		Ponticello.sendMsg('/inserted_flow_group', group.nodeID, name, score_y);
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
            };
		};
		Ponticello.sendMsg('/moved_node', node.nodeID, new_y);
	}

	* remove { |node|
		var idx = nodes.indexOf(node);
		if (idx != nil) {
			nodes.removeAt(idx);
			Ponticello.sendMsg('/removed_node', node.nodeID);
		} {
			Exception("% (ID: %) not found in AudioNodeOrder\n".format(node, node.nodeID)).reportError;
		}
	}

	* clear {
		Ponticello.sendMsg('/cleared_node_tree');
		nodes = [];
	}
}
AudioNode {
	score_y {}

	node {  }

	nodeID { ^this.node.nodeID }

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

AudioNode {
	score_y {}

	node {  }

	nodeID { ^this.node.nodeID }

	group { ^this.node }

	asTarget { ^this.node }

	moveToHead { |target| this.asTarget.moveToHead(target.asTarget) }

	moveAfter { |node| this.asTarget.moveAfter(node.asTarget) }

	moveBefore { |node| this.asTarget.moveBefore(node.asTarget) }

	release { ^this.node.release }

	isActive { ^true }
}
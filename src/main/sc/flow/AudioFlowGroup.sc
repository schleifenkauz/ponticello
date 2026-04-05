AudioFlowGroup : AudioNode {
	var <>score_y, <>node;

	* new { |score_y, node|
		^super.newCopyArgs(score_y, node);
	}
}
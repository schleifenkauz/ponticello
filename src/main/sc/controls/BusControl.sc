BusControl : ParameterControl {
	var bus, offset;

	* new { |name, bus, offset|
		^super.new(name).init(bus, offset ? 0)
	}

	init { |b, off| bus = b; offset = off }

	getBus { ^bus }

	getValue { |inst| ^bus.getSynchronous }

	getUGen { |inst| ^bus.kr }

    getSynthArgument { |inst| ^bus.subBus(offset).asMap }

	update { |new_bus|
		bus = new_bus;
		this.updateInstances { |inst|
			inst.mapParameter(this.name, bus.subBus(offset));
		}
	}

	offset_ { |val|
	    offset = val;
	    this.updateInstances { |inst|
	        inst.mapParameter(this.name, bus.subBus(offset));
	    }
    }

	asString { ^"%: %".format(name, bus) }
}
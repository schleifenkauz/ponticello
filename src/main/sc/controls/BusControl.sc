BusControl : ParameterControl {
	var bus;

	* new { |name, bus|
		^super.new(name).init(bus)
	}

	init { |b| bus = b }

	getBus { ^bus }

	getValue { |inst| ^bus.getSynchronous }

	getUGen { |inst| ^bus.kr }

    getSynthArgument { |inst| ^bus.asMap }

	update { |new_bus|
		bus = new_bus;
		this.updateInstances { |inst|
			inst.mapParameter(this.name, bus);
		}
	}

	asString { ^"%: %".format(name, bus) }
}
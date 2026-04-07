NamedObject {
	var <name, <reference;

	* dict {
		Exception("% does not override .dict".format(this)).throw;
	}

	* get { |name| ^this.dict[name] }

	* remove { |name| ^this.dict.removeAt(name) }

	* override { ^\error }

	* newCopyArgs { arg ...args;
		var name = args[0];
		var res = this.dict[name];
		^if (res.notNil) {
			if (args.size > 1) {
				if (res.respondsTo(\update)) {
					res.update(*args[1..]);
				} {
					this.override.switch(
						\error, { Exception("% '%' already defined".format(this.name, name)).throw },
						\warn, { postf("% '%' already defined\n", this.name, name) },
						\ok, { this.prCreate(name, args[1..], res.reference) }
					);
				}
			};
			res
		} {	this.prCreate(name, args[1..], NamedObjectReference(this, name)) }
	}

	* prCreate { |name, args, ref|
		var obj = super.newCopyArgs(name, ref, *args);
		this.dict[name] = obj;
		^obj
	}

	* rename { |old_name, new_name|
		^get(old_name).rename(new_name);
	}

	* includesKey { |name| ^this.dict.includesKey(name) }

	rename { |new_name|
		this.class.dict.removeAt(name);
		name = new_name;
		reference.prRenamed(new_name);
		this.class.dict[new_name] = this;
	}

	free {
		this.class.dict.removeAt(this.name);
	}

	asString { ^this.class.name.asString ++ " " ++ this.name }
}

NamedObjectReference {
	var clazz, name;

	* new { |clazz, name| ^super.newCopyArgs(clazz, name) }

	get { clazz.get(name) }

	prRenamed { |new_name|
		name = new_name;
	}

	asString { ^this.clazz.name.asString ++ " #" ++ name.asString }

	doesNotUnderstand { |msg|
		var obj = this.get;
		^if (obj.notNil) {
			performList(msg.selector, msg.args);
		} {
			super.doesNotUnderstand;
		}
	}
}
thisProcess.platform

Dialog.getPaths({}, {}, allowsMultiple:false)

(FileDialog({ |path|
path.postln
}, {

}, fileMode: 2, stripResult: true))

("which" ++ " unzip").unixCmdGetStdOut
"hello".newTextWindow

(~download = { |desc, url, path, update_progress, progressLabel|
	if (File.exists(path).not) {
		var cond = Condition.new(false);
		defer {
			progressLabel.string = desc;
			Download(
				url, path,
				finishedFunc: {
					cond.test = true;
					cond.signal;
				}, errorFunc: {
					"error".postln
				}, progressFunc: { |received, total|
					update_progress.value(received / total);
				}
			);
		};
		cond.wait;
	};
};
~execute = { |... command|
	var cond = Condition.new(false);
	postf("Executing %\n", command);
	command.unixCmd(postOutput: false) { |exit_code|
		postf("% finished with exit code %\n", command[0], exit_code);
		cond.test = true;
		cond.signal;
	};
	cond.wait;
};

~unzip = { |archive, dest, label|
	if (label.postln.notNil) {
		defer {
			label.string = "Extracting %".format(PathName(archive).fileName);
		}
	};
	~execute.("unzip", "-n", archive, "-d", dest);
};

~untar = { |archive, dest, label|
	if (label.postln.notNil) {
		defer {
			label.string = "Extracting %".format(PathName(archive).fileName);
		}
	};
	~execute.("tar", "-xvzf", archive, "-C", dest);
};
)

(
var win, buttonFile, pathField, buttonStart,
progress=0, progressLabel, progressBar, update_progress;
win = Window("Install Ponticello", Rect(200, 200, 400, 400), resizable: false);
pathField = StaticText(win, Rect(120, 20, 260, 30)).string_("No file selected");
buttonFile = Button(win, Rect(20, 20, 90, 30))
.states_([["Choose File"]])
.action_ {
	FileDialog({ |path|
		if (path.notNil) {
			pathField.string = path;
		}
	}, {}, fileMode:2, stripResult: true);
};
progressLabel = StaticText(win, Rect(20, 60, 360, 30));
progressBar = UserView(win, Rect(20, 100, 360, 20))
.background_(Color.gray(0.2))
.drawFunc_({
    var width = progressBar.bounds.width * progress;

    Pen.fillColor = Color.green;
    Pen.fillRect(Rect(0, 0, width, progressBar.bounds.height));
});
update_progress = { |prog|
	progress = prog;
	progressBar.refresh;
};

buttonStart = Button(win, Rect(20, 350, 360, 30))
.string_("Install")
.action_ {
	var dir = "/home/nikolaus/dev/ponticello/scripts/test";
	var lib = dir +/+ "lib", tmp = dir +/+ "tmp";
	File.mkdir(lib); File.mkdir(tmp);
	fork {
		var ponticelloZip, os, arch,
		jreUrl, javaZip, javafxUrl, javafxZip,
		vstPluginZip, vstPluginUrl;
		os = thisProcess.platform.name;
		arch = Platform.architecture;
		if (arch == \x86_64) { arch = \x64 };
		if (arch == \arm64) { arch = \aarch64 };

		javaZip = tmp +/+ "jre.tar.gz";
		jreUrl = "https://download.oracle.com/java/21/latest/jdk-21_%-%_bin.tar.gz".format(os, arch);
		~download.(
			"Downloading Java Runtime Environment", jreUrl, javaZip,
			update_progress, progressLabel,
		);
		~untar.(javaZip, tmp, progressLabel);

		javafxUrl = "https://download2.gluonhq.com/openjfx/21.0.8/openjfx-21.0.8_%-%_bin-sdk.zip".format(os, arch);
		javafxZip = tmp +/+ "javafx-sdk.zip";
		~download.(
			"Downloading JavaFX SDK", javafxUrl, javafxZip,
			update_progress, progressLabel
		);
		~unzip.(javafxZip, tmp, progressLabel);

		File.mkdir(lib);
		if (File.exists(lib ++ "/javafx").not) {
			File.copy(tmp ++ "/javafx-sdk-21.0.8", lib ++ "/javafx");
		};
		if (File.exists(lib ++ "/jdk").not) {
			File.copy(tmp ++ "/jdk-21.0.10", lib ++ "/jdk");
		};

		if (Class.allClasses.any { |cls| cls.name == \VSTPlugin }.not || true) {
			vstPluginUrl = Platform.case(
				\windows, { "https://git.iem.at/-/project/485/uploads/c262d8a3f23a03d761c5d7575772480d/vstplugin_v0.6.2_win64.zip" },
				\linux, {
					switch(arch)
					{\x64} { "https://git.iem.at/-/project/485/uploads/7dcb287f5cc882fd3aa6f157cdc7bb5d/vstplugin_v0.6.2_Linux.zip" }
					{\aarch64} { "https://git.iem.at/-/project/485/uploads/c3466098d71e135c229dccf3703ee263/vstplugin_v0.6.2_Linux_ARM64.zip" }
				},
				\osx, { "https://git.iem.at/-/project/485/uploads/d7d04b43c6e025d00ddb7cd73217aadf/vstplugin_v0.6.2_macOS.zip" }
			);
			vstPluginZip = tmp +/+ "vstplugin.zip";
			~download.(
				"Downloading VSTPlugin extension", vstPluginUrl, vstPluginZip,
				update_progress, progressLabel
			);
			~unzip.(vstPluginZip, tmp +/+ "vstplugin", progressLabel);
			if (File.exists(Platform.userExtensionDir +/+ "VSTPlugin2").not) {
				File.copy(tmp +/+ "vstplugin/sc/VSTPlugin", Platform.userExtensionDir +/+ "VSTPlugin2");
			}
		};

		ponticelloZip = dir +/+ "ponticello.zip";
		~download.(
			"Downloading Ponticello...",
			"https://schleifenkauz.de/software/ponticello-v0.2.2.zip",
			ponticelloZip,
			update_progress, progressLabel
		);
		~unzip.(ponticelloZip, dir, progressLabel);

		if (File.exists(Platform.userExtensionDir +/+ "ponticello_extensions").not) {
			File.copy(dir +/+ "extensions", Platform.userExtensionDir +/+ "ponticello_extensions");
		};

		//File.delete(tmp);
	}
};
win.front
)

Platform.userExtensionDir.openOS
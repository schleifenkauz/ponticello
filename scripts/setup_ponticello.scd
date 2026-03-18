(~download = { |desc, url, path|
	if (File.exists(path).postln.not) {
		var cond = Condition.new(false);
		defer {
			~progressLabel.string = desc;
			Download(
				url, path,
				finishedFunc: {
					cond.test = true;
					cond.signal;
				}, errorFunc: {
					"error".postln
				}, progressFunc: { |received, total|
					~progress = (received / total);
					~progressBar.refresh;
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
	if (label.notNil) {
		defer {
			label.string = "Extracting %".format(PathName(archive).fileName);
		}
	};
	~execute.("unzip", "-n", archive, "-d", dest);
};

~untar = { |archive, dest, label|
	if (label.notNil) {
		defer {
			label.string = "Extracting %".format(PathName(archive).fileName);
		}
	};
	~execute.("tar", "-xvzf", archive, "-C", dest);
};

~move = { |item, dest|
	var cmd = Platform.case(
		\osx, "mv", \linux, "mv",
		\windows, "move"
	);
	if (File.exists(dest).not) {
		[cmd, item, dest].postln.unixCmd;
	} {
		~delete.(item);
	};
};

~delete = { |item|
	if (thisProcess.platform.name == \windows) {
		["rmdir", "/s", item].postln.unixCmd;
	} {
		["rm", "-rf", item].postln.unixCmd;
	}
};


~top = 10;

~heading_font = Font("Monaco", 14, bold: true);
~win = Window("Install Ponticello", Rect(200, 200, 400, 400), resizable: false);

StaticText(~win, Rect(20, ~top, 360, 30))
.string_("Installation Directory")
.font_(Font("Moncao", 14, bold: true));
~installationDirChooser = Button(~win, Rect(20, ~top = ~top + 25, 60, 30))
.states_([["Choose"]])
.action_ {
	FileDialog({ |path|
		if (path.notNil) {
			~installationDirLabel.string = path;
		}
	}, {}, fileMode:2, stripResult: true);
};
~installationDirLabel = StaticText(~win, Rect(90, ~top, 260, 30)).string_(Platform.userHomeDir +/+ ".local/share/ponticello");

~jrePath = "JAVA_HOME".getenv;
StaticText(~win, Rect(20, ~top = ~top + 35, 360, 30))
.string_("Java Runtime Environment").font_(~heading_font);
~jreOptionButton = PopUpMenu(~win, Rect(20, ~top = ~top + 25, 70, 30))
.items_(["Select", "Locate", "Install"])
.action_({ |view|
	view.items[view.value].switch(
		"Locate", {
			FileDialog({ |path|
				if (path.notNil) {
					~javaBin = path ++ "/bin/java";
					[~javaBin, "--version"].unixCmd { |exit_code|
						defer {
							if (File.exists(~javaBin) && (exit_code.postln == 0)) {
								~jrePath = path;
								~jrePathLabel.string = ~jrePath;
								view.items = ["Locate", "Install"];
								view.value = 0;
								~installBtn.enabled = true;
							} {
								var alert = Window("Alert", Rect(200, 200, 250, 70), resizable: false)
								.alwaysOnTop_(true);
								StaticText(alert, Rect(20, 10, 230, 30)).string_("Not a valid JDK directory.");
								Button(alert, Rect(100, 40, 50, 25)).states_([["Ok"]])
								.action_{ alert.close };
								~jreOptionButton.value = case
								{ ~jreOptionButton.items.size == 3 } { 0 }
								{ ~jrePath.isNil } { 1 }
								{ true } { 0 };
								alert.front;
							}
						}
					}
				}
			}, {}, fileMode: 2, stripResult: true);
		},
		"Install", {
			~jrePath = nil;
			~jrePathLabel.string = "<Install>";
			view.items = ["Locate", "Install"];
			view.value = 1;
			~installBtn.enabled = true;
		}
	)
});
~jrePathLabel = StaticText(~win, Rect(100, ~top, 280, 30));

StaticText(~win, Rect(20, ~top = ~top + 35, 360, 30))
.string_("Optional Features").font_(~heading_font);

~option_instr_library = CheckBox(~win, Rect(20, ~top = ~top + 25, 300, 30), "Instrument/Effect Library")
.action_ { |val| ~instr_library = val.value };
~option_conductor = CheckBox(~win, Rect(20, ~top = ~top + 25, 300, 30), "Integrated Conductor Interface")
.action_ { |val| ~conductor = val.value };
~option_vstplugin = CheckBox(~win, Rect(20, ~top = ~top + 25, 300, 30), "Support for VST Plugins")
.action_ { |val| ~vstplugin = val.value };
if (thisProcess.platform.name == \linux) {
	~option_launcher = CheckBox(~win, Rect(20, ~top = ~top + 25, 300, 30), "Create Launcher")
	.action_ { |val| ~launcher = val.value };
};

~installBtn = Button(~win, Rect(20, 350, 360, 30))
.string_("Install").enabled_(false)
.action_ {
	~installDir = ~installationDirLabel.string;
	~libDir = ~installDir +/+ "lib"; ~tmp = ~installDir +/+ "tmp";
	File.mkdir(~libDir); File.mkdir(~tmp);

	~progress = 0;
	~progressLabel = StaticText(~win, Rect(20, 290, 360, 30));
	~progressBar = UserView(~win, Rect(20, 320, 360, 20))
	.background_(Color.gray(0.2))
	.drawFunc_({
		var width = ~progressBar.bounds.width * ~progress;

		Pen.fillColor = Color.green;
		Pen.fillRect(Rect(0, 0, width, ~progressBar.bounds.height));
	});
	~update_progress = { |prog|
		~progress = prog;
		~progressBar.refresh;
	};


	~installBtn.enabled = false;
	~jreOptionButton.enabled = false;
	~installationDirChooser.enabled = false;
	~option_instr_library.enabled = false;
	~option_conductor.enabled = false;
	~option_vstplugin.enabled = false;
	~option_launcher.enabled = false;

	fork {
		~os = thisProcess.platform.name;
		~arch = Platform.architecture;
		if (~arch == \x86_64) {~arch = \x64 };
		if (~arch == \arm64) {~arch = \aarch64 };

		if (~jrePath.postln.isNil) {
			~jrePath = ~libDir ++ "/jdk";
			~javaZip = ~tmp +/+ "jre.tar.gz";
			~jreUrl = "https://download.oracle.com/java/21/latest/jdk-21_%-%_bin.tar.gz".format(~os,~arch);
			~download.(
				"Downloading Java Runtime Environment...", ~jreUrl, ~javaZip
			);
			~untar.(~javaZip, ~tmp, ~progressLabel);
			~move.(~tmp ++ "/jdk-21.0.10", ~jrePath);
		};

		~javafxUrl = "https://download2.gluonhq.com/openjfx/21.0.8/openjfx-21.0.8_%-%_bin-sdk.zip".format(~os,~arch);
		~javafxZip = ~tmp +/+ "javafx-sdk.zip";
		~download.(
			"Downloading JavaFX SDK...", ~javafxUrl, ~javafxZip
		);
		~unzip.(~javafxZip, ~tmp, ~progressLabel);
		~move.(~tmp ++ "/javafx-sdk-21.0.8", ~libDir ++ "/javafx");

		if (Class.allClasses.any { |cls| cls.name == \VSTPlugin }.not && (~vstplugin == true)) {
			~vstPluginUrl = Platform.case(
				\windows, { "https://git.iem.at/-/project/485/uploads/c262d8a3f23a03d761c5d7575772480d/vstplugin_v0.6.2_~win64.zip" },
				\linux, {
					switch(~arch)
					{\x64} { "https://git.iem.at/-/project/485/uploads/7dcb287f5cc882fd3aa6f157cdc7bb5d/vstplugin_v0.6.2_Linux.zip" }
					{\aarch64} { "https://git.iem.at/-/project/485/uploads/c3466098d71e135c229dccf3703ee263/vstplugin_v0.6.2_Linux_ARM64.zip" }
				},
				\osx, { "https://git.iem.at/-/project/485/uploads/d7d04b43c6e025d00ddb7cd73217aadf/vstplugin_v0.6.2_macOS.zip" }
			);
			~vstPluginZip = ~tmp +/+ "vstplugin.zip";
			~download.(
				"Downloading VSTPlugin extension", ~vstPluginUrl, ~vstPluginZip
			);
			~unzip.(~vstPluginZip, ~tmp +/+ "vstplugin", ~progressLabel);
			~move.(~tmp +/+ "vstplugin/sc/VSTPlugin", Platform.userExtensionDir +/+ "VSTPlugin");
		};

		~ponticelloZip = ~tmp +/+ "ponticello.zip";
		~download.(
			"Downloading Ponticello...",
			"https://schleifenkauz.de/software/ponticello-v0.2.2.zip",
			~ponticelloZip,
		);
		~unzip.(~ponticelloZip, ~installDir, ~progressLabel);
		~move.(~installDir +/+ "extensions", Platform.userExtensionDir +/+ "ponticello");

		if (~instr_library != true) {
			~delete.(~installDir +/+ "global-instruments");
		};

		~shebang = if (thisProcess.platform.name == \windows) { "" } {
			"#!/usr/bin/env bash\n"
		};
		~startCommand = "% % -jar --module-path % --add-modules javafx.graphics --add-modules javafx.web %"
				.format(
					~shebang,
					(~jrePath +/+ "bin/java").shellQuote,
					(~libDir +/+ "javafx/lib").shellQuote,
					(~installDir +/+ "app/ponticello.jar").shellQuote
				);
		~startCommandFile =
		if (thisProcess.platform.name == \windows)
		{~installDir +/+ "ponticello.bat"} {~installDir +/+ "ponticello"};
		File.use(~startCommandFile, "w", _.write(~startCommand));
		if (thisProcess.platform.name != \windows) {
			["chmod", "+x", ~startCommandFile].unixCmd;
		};

		if (~launcher.postln == true) {
			~desktop_file_content = File.use((~installDir +/+ "resources/Ponticello.desktop").postln, "r", _.readAllString)
			.replace("<ponticello_home>", ~installDir).postln;
			~desktop_files = Platform.userHomeDir +/+ ".local/share/applications";
			File.use((~desktop_files +/+ "Ponticello.desktop").postln, "w", _.write(~desktop_file_content));
		} {
			~delete.(~installDir +/+ "resources");
		};

		if (~conductor) {
			//TODO install Conductor Interface
		};

		//File.delete(~tmp);

		defer {
			~progressLabel.string = "Completed installation.";
			~installBtn.remove;
			Button(~win, Rect(20, 350, 170, 30)).string_("Finish installation")
			.action_ { ~win.close };
			Button(~win, Rect(210, 350, 170, 30)).string_("Start Ponticello")
			.action_ {
				~win.close;
				~startCommandFile.unixCmd;
			};
		};
		"Finished!".postln;
	}
};
~win.front
)
ServerMeterViewCL {

	classvar serverMeterViews, updateFreq = 5, dBLow = -80, <height = 230;
	classvar serverCleanupFuncs;

	var <view;
	var inresp, outresp, synthFunc, responderFunc, server, numIns, numOuts, inmeters, outmeters, startResponderFunc, meterWidth, gapWidth;

	*new { |aserver, parent, leftUp, numIns, numOuts, meterWidth, gapWidth|
		^super.new.init(aserver, parent, leftUp, numIns, numOuts, meterWidth, gapWidth)
	}

	*getWidth { arg numIns, numOuts, meterWidth, gapWidth, server;
		^2/*20*/+((numIns + numOuts + 2) * (meterWidth + gapWidth))
	}

	init { arg aserver, parent, leftUp, anumIns, anumOuts, meterWidth, gapWidth;
		var innerView, viewWidth, levelIndic, palette;

		server = aserver;

		numIns = anumIns ?? { server.options.numInputBusChannels };
		numOuts = anumOuts ?? { server.options.numOutputBusChannels };

		viewWidth= this.class.getWidth(anumIns, anumOuts, meterWidth, gapWidth);

		leftUp = leftUp ? (0@0);

		view = CompositeView(parent, Rect(leftUp.x, leftUp.y, viewWidth, height) );
		view.onClose_( { this.stop });
		innerView = CompositeView(view, Rect(7/*10*/, 3/*25*/, viewWidth, height) ); // Rect(3 si -> numOuts==10 ou 18 ou 20
		innerView.addFlowLayout(0@0, gapWidth@gapWidth);

		// dB scale
		UserView(innerView, Rect(0, 0, meterWidth, 95/*195*/)).drawFunc_( {
			try {
				Pen.color = \QPalette.asClass.new.windowText;
			} {
				Pen.color = Color.white;
			};
			Pen.font = Font.sansSerif(10).boldVariant;
			// Pen.stringCenteredIn("0", Rect(0, 0, meterWidth, 12));
			// Pen.stringCenteredIn("-80", Rect(0, 70/*170*/, meterWidth, 12));
		});

		if(numIns > 0) {
			// ins
			/*StaticText(view, Rect(10, 5, 100, 15))
			.font_(Font.sansSerif(10).boldVariant)
			.string_("Inputs");*/
			inmeters = Array.fill( numIns, { arg i;
				var comp;
				comp = CompositeView(innerView, Rect(0, 0, meterWidth, 95/*195*/))/*.resize_(5)*/; // .resize_(5); pas commenté si numOuts==10 ou 18 ou 20
				StaticText(comp, Rect(0, 80/*180*/, meterWidth, 15))
				.font_(Font.sansSerif(9).boldVariant)
				.string_((i+1).asString);
				levelIndic = LevelIndicator( comp, Rect(0, 0, meterWidth, 80/*180*/) ).warning_(0.9).critical_(1.0)
				.drawsPeak_(true)
				.numTicks_(9)
				.numMajorTicks_(3)
			});
		};

		if((numIns > 0) && (numOuts > 0)) {
			// divider
			UserView(innerView, Rect(0, 0, meterWidth, 80/*180*/)).drawFunc_( {
				try {
					Pen.color = \QPalette.asClass.new.windowText;
				} {
					Pen.color = Color.white;
				};
				Pen.line(((meterWidth + gapWidth) * 0.5)@0, ((meterWidth + gapWidth) * 0.5)@80/*180*/);
				Pen.stroke;
			});
		};

		// outs
		if(numOuts > 0) {
			/*StaticText(view, Rect(10 + if(numIns > 0) { (numIns + 2) * (meterWidth + gapWidth) } { 0 }, 5, 100, 15))
			.font_(Font.sansSerif(10).boldVariant)
			.string_("Outputs");*/
			outmeters = Array.fill( numOuts, { arg i;
				var comp;
				comp = CompositeView(innerView, Rect(0, 0, meterWidth, 95/*195*/));
				// StaticText(comp, Rect(0, 80/*180*/, meterWidth, 15)) si numOuts==10 ou 18 ou 20
				// .font_(Font.sansSerif(9).boldVariant) si numOuts==10 ou 18 ou 20
				StaticText(comp, Rect(0, 83/*180*/, meterWidth /*-3*/, 40)).align_(\topLeft) // meterWidth -5 si numOuts==27
				.font_(Font.sansSerif(8).boldVariant)
				.string_((i+1).asString)
				.stringColor_( case
					{numOuts==10 or:{numOuts==18} or:{numOuts==20}}
					{case {i<8} {Color.yellow}  {i>15} {Color.yellow} {i<16} {Color.blue}}
					{numOuts==27}
					{case {i<8} {Color.yellow}  {i.inclusivelyBetween(8,15)} {Color.red}  {i.inclusivelyBetween(16,23)} {Color.blue} {i>23} {Color.yellow}}
					{numOuts==32}
					{case {i<11} {Color.yellow}  {i.inclusivelyBetween(11,21)} {Color.red}  {i.inclusivelyBetween(21,30)} {Color.blue} {i>30} {Color.yellow}}
					{numOuts==34}
					{case {i<12} {Color.yellow}  {i.inclusivelyBetween(12,21)} {Color.red}  {i.inclusivelyBetween(22,31)} {Color.blue} {i>31} {Color.yellow}}
					{numOuts==64 or:{numOuts==96} or:{numOuts==128}}
					{case {i<16} {Color.yellow}  {i.inclusivelyBetween(16,31)} {Color.red}  {i.inclusivelyBetween(32,47)} {Color.yellow} {i>48} {Color.blue}}
					{numOuts>0}
					{case {i<8} {Color.yellow}  {i>15} {Color.yellow} {i<16} {Color.blue}};
				);
				levelIndic = LevelIndicator( comp, Rect(0, 0, meterWidth, 80/*180*/) ).warning_(0.9).critical_(1.0)
				.drawsPeak_(true)
				.numTicks_(9)
				.numMajorTicks_(3)
				.background_(Color.white)
			});
		};

		this.setSynthFunc(inmeters, outmeters);
		startResponderFunc = {this.startResponders};
		this.start;
	}

	setSynthFunc {
		var numRMSSamps, numRMSSampsRecip;

		synthFunc = {
			//responders and synths are started only once per server
			var numIns = server.options.numInputBusChannels;

			var numOuts = server.options.numOutputBusChannels;
			numRMSSamps = server.sampleRate / updateFreq;
			numRMSSampsRecip = 1 / numRMSSamps;

			server.bind( {
				var insynth, outsynth;
				if(numIns > 0, {
					insynth = SynthDef(server.name ++ "InputLevels", {
						var in = In.ar(NumOutputBuses.ir, numIns);
						SendPeakRMS.kr(in, updateFreq, 3, "/" ++ server.name ++ "InLevels")
					}).play(RootNode(server), nil, \addToHead);
				});
				if(numOuts > 0, {
					outsynth = SynthDef(server.name ++ "OutputLevels", {
						var in = In.ar(0, numOuts);
						SendPeakRMS.kr(in, updateFreq, 3, "/" ++ server.name ++ "OutLevels")
					}).play(RootNode(server), nil, \addToTail);
				});

				if (serverCleanupFuncs.isNil) {
					serverCleanupFuncs = IdentityDictionary.new;
				};
				serverCleanupFuncs.put(server, {
					insynth.free;
					outsynth.free;
					ServerTree.remove(synthFunc, server);
				});
			});
		};
	}

	startResponders {
		var numRMSSamps, numRMSSampsRecip;

		//responders and synths are started only once per server
		numRMSSamps = server.sampleRate / updateFreq;
		numRMSSampsRecip = 1 / numRMSSamps;
		if(numIns > 0) {
			inresp = OSCFunc( {|msg|
				{
					try {
						var channelCount = min(msg.size - 3 / 2, numIns);

						channelCount.do {|channel|
							var baseIndex = 3 + (2*channel);
							var peakLevel = msg.at(baseIndex);
							var rmsValue = msg.at(baseIndex + 1);
							var meter = inmeters.at(channel);
							if (meter.notNil) {
								if (meter.isClosed.not) {
									meter.peakLevel = peakLevel.ampdb.linlin(dBLow, 0, 0, 1, \min);
									meter.value = rmsValue.ampdb.linlin(dBLow, 0, 0, 1);
								}
							}
						}
					} { |error|
						if(error.isKindOf(PrimitiveFailedError).not) { error.throw }
					};
				}.defer;
			}, ("/" ++ server.name ++ "InLevels").asSymbol, server.addr).fix;
		};
		if(numOuts > 0) {
			outresp = OSCFunc( {|msg|
				{
					try {
						var channelCount = min(msg.size - 3 / 2, numOuts);

						channelCount.do {|channel|
							var baseIndex = 3 + (2*channel);
							var peakLevel = msg.at(baseIndex);
							var rmsValue = msg.at(baseIndex + 1);
							var meter = outmeters.at(channel);
							if (meter.notNil) {
								if (meter.isClosed.not) {
									meter.peakLevel = peakLevel.ampdb.linlin(dBLow, 0, 0, 1, \min);
									meter.value = rmsValue.ampdb.linlin(dBLow, 0, 0, 1);
								}
							}
						}
					} { |error|
						if(error.isKindOf(PrimitiveFailedError).not) { error.throw }
					};
				}.defer;
			}, ("/" ++ server.name ++ "OutLevels").asSymbol, server.addr).fix;
		};
	}

	start {
		if(serverMeterViews.isNil) {
			serverMeterViews = IdentityDictionary.new;
		};
		if(serverMeterViews[server].isNil) {
			serverMeterViews.put(server, List());
		};
		if(serverMeterViews[server].size == 0) {
			ServerTree.add(synthFunc, server);
			if(server.serverRunning, synthFunc); // otherwise starts when booted
		};
		serverMeterViews[server].add(this);
		if (server.serverRunning) {
			this.startResponders
		} {
			ServerBoot.add (startResponderFunc, server)
		}
	}

	stop {
		serverMeterViews[server].remove(this);
		if(serverMeterViews[server].size == 0 and: (serverCleanupFuncs.notNil)) {
			serverCleanupFuncs[server].value;
			serverCleanupFuncs.removeAt(server);
		};

		(numIns > 0).if( { inresp.free; });
		(numOuts > 0).if( { outresp.free; });

		ServerBoot.remove(startResponderFunc, server)
	}

	remove {
		view.remove
	}
}

ServerMeterCL {

	var <window, <meterView;

	*new { |server, numIns, numOuts|

		var window, meterView;

		numIns = numIns ?? { server.options.numInputBusChannels };
		numOuts = numOuts ?? { server.options.numOutputBusChannels };

		window = Window.new(server.name ++ " levels (dBFS)",
			Rect(5, 305, ServerMeterViewCL.getWidth(numIns, numOuts), ServerMeterViewCL.height),
			false);

		meterView = ServerMeterViewCL(server, window, 0@0, numIns, numOuts);
		meterView.view.keyDownAction_( { arg view, char, modifiers;
			if(modifiers & 16515072 == 0) {
				case
					{char === 27.asAscii } { window.close };
			};
		});

		window.front;

		^super.newCopyArgs(window, meterView)

	}
}

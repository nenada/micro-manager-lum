package org.lumencor.targa;

import com.google.common.eventbus.Subscribe;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.events.ChannelExposureEvent;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Adapter between Micro-Manager 2.0 plugin API and the Targa acquisition plugin
 * @author Milos Jovanovic <milos@tehnocad.rs>
 */
@Plugin(type = MenuPlugin.class)
public class TargaPlugin implements MenuPlugin, SciJavaPlugin {
	public static final String VERSION_INFO = "1.0.0";
	private static final String COPYRIGHT_NOTICE = "Copyright (C) by Lumencor, 2024";
	private static final String DESCRIPTION = "Image acquisition for Targa Microscope";
	private static final String NAME = "Targa";

	private Studio studio_;
	private TargaAcqWindow frame_;

	@Override
	public void setContext(Studio studio) {
		studio_ = studio;
		studio_.events().registerForEvents(this);
	}

	@Override
	public String getSubMenu() {
		return "Acquisition Tools";
	}

	@Override
	public String getCopyright() {
		return COPYRIGHT_NOTICE;
	}

	@Override
	public String getHelpText() {
		return DESCRIPTION;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getVersion() {
		return VERSION_INFO;
	}

	@Override
	public void onPluginSelected() {
		if(frame_ == null)
			frame_ = new TargaAcqWindow(studio_);
		frame_.setVisible(true);
	}

	/**
	 * Event signalling that the camera exposure time associated with a
	 * configgroup-configpreset has been changed.
	 *
	 * @param event info about the event.
	 */
	@Subscribe
	public void onChannelExposure(ChannelExposureEvent event) {
		if(frame_ == null)
			return;
		double nexp = event.getNewExposureTime();
		frame_.updateExposure(nexp);
	}
}

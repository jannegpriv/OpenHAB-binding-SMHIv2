package org.openhab.binding.smhi.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Extension of the default OSGi bundle activator
 * 
 * @author Jan Gustafsson
 * @author Mattias Markehed
 */
public final class SmhiActivator implements BundleActivator {

	private static Logger logger = LoggerFactory.getLogger(SmhiActivator.class); 
	
	/**
	 * Called whenever the OSGi framework starts our bundle
	 */
	public void start(BundleContext bc) throws Exception {
		logger.debug("SMHI binding has been started.");
	}

	/**
	 * Called whenever the OSGi framework stops our bundle
	 */
	public void stop(BundleContext bc) throws Exception {
		logger.debug("SMHI binding has been stopped.");
	}
	
}

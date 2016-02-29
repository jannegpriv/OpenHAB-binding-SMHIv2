package org.openhab.binding.smhi;

import org.openhab.core.binding.BindingProvider;

/**
 * @author Jan Gustafsson
 * @author Mattias Markehed
 */
public interface SmhiBindingProvider extends BindingProvider {
	
	public double getLongitude(String itemName);
	public double getLatitude(String itemName);
	public String getParameter(String itemName);

}

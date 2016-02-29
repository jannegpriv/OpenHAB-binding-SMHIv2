package org.openhab.binding.smhi.internal;

import org.openhab.binding.smhi.SmhiBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.NumberItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;


/**
 * This class is responsible for parsing the binding configuration.
 * 
 * @author Jan Gustafsson
 * @author Mattias Markehed
 */
public class SmhiGenericBindingProvider extends AbstractGenericBindingProvider implements SmhiBindingProvider {
	
	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "smhi";
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
		if (!(item instanceof NumberItem)) {
			throw new BindingConfigParseException("item '" + item.getName()
					+ "' is of type '" + item.getClass().getSimpleName()
					+ "', only Number are allowed - please check your *.items configuration");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item, String bindingConfig) throws BindingConfigParseException {
		
		SmhiBindingConfig config = new SmhiBindingConfig();
		if (bindingConfig.trim().contains(":")) {
			String[] configParts = bindingConfig.trim().split(":");
			if (configParts.length != 3) {
				throw new BindingConfigParseException("Smhi binding configuration must contain three parts");
			}
			else {
				config.latitude =  Double.valueOf(configParts[0]);
				config.longitude = Double.valueOf(configParts[1]);
				config.parameter = String.valueOf(configParts[2]).toLowerCase();
			}
		}
		else {
			config.parameter = bindingConfig.toLowerCase();
		}
		
		addBindingConfig(item, config);
	}
	
	@Override
	public String getParameter(String itemName) {
		SmhiBindingConfig config = (SmhiBindingConfig) bindingConfigs.get(itemName);
		return config != null ? config.parameter : null;
	}
	
	@Override
	public double getLongitude(String itemName) {
		SmhiBindingConfig config = (SmhiBindingConfig) bindingConfigs.get(itemName);
		return config != null ? config.longitude : 0;
	}

	@Override
	public double getLatitude(String itemName) {
		SmhiBindingConfig config = (SmhiBindingConfig) bindingConfigs.get(itemName);
		return config != null ? config.latitude : 0;
	}
	
	/**
	 * Data structure representing the item configuration. 
	 */
	static private class SmhiBindingConfig implements BindingConfig {
		
		public double longitude = 0;
		public double latitude = 0;
		
		/** The data to fetch. Valid parameters can be found in {@link org.openhab.binding.smhi.internal.SmhiConstants }*/
		public String parameter;
		
	}
}

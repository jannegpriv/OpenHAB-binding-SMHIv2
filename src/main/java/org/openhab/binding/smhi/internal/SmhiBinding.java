package org.openhab.binding.smhi.internal;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.openhab.binding.smhi.SmhiBindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.library.types.DecimalType;
import org.openhab.io.net.http.HttpUtil;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The SMHI binding Refresh Service polls weather data from the SMHI servers 
 * with a fixed interval and posts a new event of type ({@link DecimalType} to the event bus.
 * The defalt interval is 10 minutes. 
 * 
 * @author Jan Gustafsson
 * @author Mattias Markehed
 */
public class SmhiBinding extends AbstractActiveBinding<SmhiBindingProvider> implements ManagedService {

	private static final Logger logger = 
			LoggerFactory.getLogger(SmhiBinding.class);

	// Keeps track of the last time item was updated. 
	private Map<String, Long> lastUpdateMap = new HashMap<String, Long>();
	
	// Keeps track of the last time item was updated. 
	private Map<Geometry, WeatherDataV2> actualWeatherDataMap = new HashMap<Geometry, WeatherDataV2>();
	
	// The server used to store the SMHI weather data. */
	protected static final String URL = "http://opendata-download-metfcst.smhi.se/api/category/pmp2g/version/2/geotype/point/lon/%s/lat/%s/data.json";
	
	// JSON mapper
	private static final ObjectMapper JSON = new ObjectMapper();
	
	// Update with 10 minutes interval.
	private long refreshInterval = 600000L;
	
	// Timeout for weather data requests.
	private static final int SMHI_TIMEOUT = 5000;
	
	// Are optionally read from openhab.cfg
	private double homeLatitude = 0;
	private double homeLongitude = 0;
	
	// Config latitude
	private static String CONFIG_KEY_LATITUDE = "home.latitude";
	
	// Config longitude
	private static String CONFIG_KEY_LONGITUDE = "home.longitude";
	
	//Config refresh interval
	private static String CONFIG_KEY_REFRESH = "refresh";
	
	@Override
	protected String getName() {
		return "SMHI Refresh Service";
	}
	
	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}
	
	private SmhiDataListV2 executeQuery(double longitude, double latitude) {
		// SMHI API only supports 6 digits in API call
		DecimalFormat df = new DecimalFormat("##.######");
		DecimalFormatSymbols custom=new DecimalFormatSymbols();
		custom.setDecimalSeparator('.');
		df.setDecimalFormatSymbols(custom);
		String apiRequest = String.format(URL, df.format(longitude), df.format(latitude));
		
		String apiResponseJson = null;
		SmhiDataListV2 dataList = null;
		
		try {
			apiResponseJson = HttpUtil.executeUrl("GET", apiRequest, null, null, "application/json", SMHI_TIMEOUT);
			logger.debug("Quering SMHI API: " + apiRequest);
			dataList = JSON.readValue(apiResponseJson, SmhiDataListV2.class);
		} catch (final Exception e) {
			if (e instanceof JsonMappingException) {
				logger.error("Could not parse JSON from URL '"
						+ URL + "' content='" + apiRequest + "' json='" + apiResponseJson + "' Exception trace:'" +
						e.toString());
			}
			else {
				logger.error("'Exception trace:'" + e.toString());
			}
		}
		return dataList;
	}
		
	/**
	 * @{inheritDoc}
	 */
	@Override
	public void execute() {
				
		if (!bindingsExist()) {
			logger.info("There is no existing SMHI binding configuration => refresh cycle aborted!");
			return;
		}
		
		for (SmhiBindingProvider provider : providers) {
			for (String itemName : provider.getItemNames()) {
				
				Long lastUpdateTimeStamp = lastUpdateMap.get(itemName);
				if (lastUpdateTimeStamp == null) {
					lastUpdateTimeStamp = 0L;
				}
				
				long timeSinceLastRefresh = System.currentTimeMillis() - lastUpdateTimeStamp;
				boolean needsUpdate = timeSinceLastRefresh >= refreshInterval;
				
				double longitude = 0;
				double latitude = 0;
				
				if (needsUpdate) {		
					if (provider.getLongitude(itemName) != 0) {
						longitude = provider.getLongitude(itemName);
					}
					else {
						longitude = homeLongitude; 
					}
					if (provider.getLatitude(itemName) != 0) {
						latitude = provider.getLatitude(itemName);
					}
					else {
						latitude = homeLatitude;
					}
	
					List<Double> tmpPos = new ArrayList<Double>();
					tmpPos.add(longitude);
					tmpPos.add(latitude);
					List<List<Double>> tmpPosList = new ArrayList<List<Double>>();
					tmpPosList.add(tmpPos);
					Geometry point = new Geometry(tmpPosList);
					 
					// Check if actual weather data for point is already cached
					WeatherDataV2 actualWeatherData = actualWeatherDataMap.get(point);
					
					if (actualWeatherData == null) {
						// Query SMHI API
						SmhiDataListV2 dataList = executeQuery(longitude, latitude);
						if (dataList != null) {
						
							// Find in time matching time serie
							Date now = new Date();
							List<WeatherDataV2> timeSeries = dataList.getTimeSeries();
							while (timeSeries.get(0).getValidTime().before(now) && timeSeries.size() > 1) {
								timeSeries.remove(0);
							}

							// Fetch actual data serie
							actualWeatherData = timeSeries.get(0);
							// Populate values
							actualWeatherData.processData();
							// Polulate internal map with API request results
							actualWeatherDataMap.put(point, actualWeatherData);
						}
						else {
							logger.error("SMHI API query failed!");
							return;
						}
					}

					// Try to find value of seeked parameter
					double value = -1;
					
					switch (provider.getParameter(itemName)) {
					case SmhiConstants.PARAMETER_TEMPERATURE:
						value = actualWeatherData.getTemperature();
						break;
					case SmhiConstants.PARAMETER_THUNDERSTORM:
						value = actualWeatherData.getProbabilityThunderstorm();
						break;
					case SmhiConstants.PARAMETER_TOTAL_CLOUD_COVER:
						value = actualWeatherData.getTotalCloudCover();
						break;
					case SmhiConstants.PARAMETER_HIGH_CLOUD_COVER:
						value = actualWeatherData.getHighCloudCover();
						break;
					case SmhiConstants.PARAMETER_MEDIUM_CLOUD_COVER:
						value = actualWeatherData.getMediumCloudCover();
						break;
					case SmhiConstants.PARAMETER_LOW_CLOUD_COVER:
						value = actualWeatherData.getLowCloudCover();
						break;	
					case SmhiConstants.PARAMETER_HUMIDITY:
						value = actualWeatherData.getHumidity();
						break;
					case SmhiConstants.PARAMETER_MAX_PRECIPITATION:
						value = actualWeatherData.getMaxPrecipitation();
						break;
					case SmhiConstants.PARAMETER_MIN_PRECIPITATION:
						value = actualWeatherData.getMinPrecipitation();
						break;
					case SmhiConstants.PARAMETER_FROZEN_PRECIPITATION:
						value = actualWeatherData.getFroozenPrecipitation();
						break;
					case SmhiConstants.PARAMETER_PRECIPITATION_CATEGORY:
						value = actualWeatherData.getPrecipitationCategory();
						break;
					case SmhiConstants.PARAMETER_MEDIAN_PRECIPITATION:
						value = actualWeatherData.getMedianPrecipitation();
						break;
					case SmhiConstants.PARAMETER_MEAN_PRECIPITATION:
						value = actualWeatherData.getMeanPrecipitation();
						break;
					case SmhiConstants.PARAMETER_PRESSURE:
						value = actualWeatherData.getPressure();
						break;
					case SmhiConstants.PARAMETER_VISIBILITY:
						value = actualWeatherData.getVisibility();
						break;
					case SmhiConstants.PARAMETER_WIND_DIRECTION:
						value = actualWeatherData.getWindDirection();
						break;
					case SmhiConstants.PARAMETER_WIND_GUST:
						value = actualWeatherData.getWindGust();
						break;
					case SmhiConstants.PARAMETER_WIND_VELOCITY:
						value = actualWeatherData.getWindVelocity();
						break;
					default:
						value = -1;
					}

					if (value != -1){
						eventPublisher.postUpdate(itemName, new DecimalType(value));
					}
					lastUpdateMap.put(itemName, System.currentTimeMillis());
				}	
				else {
					logger.debug("Not time to refresh item: " + itemName);
				}
			}
		}
		// Clear all cached weather data
		actualWeatherDataMap.clear();
	}
		
	/**
	 * {@inheritDoc}
	 */
	public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
		
		logger.debug("Updated called");
		if (properties != null) {
			logger.debug("Updated called, properties are not null");
			String cfgLatitude = (String) properties.get(CONFIG_KEY_LATITUDE);
			String cfgLongitude = (String) properties.get(CONFIG_KEY_LONGITUDE);
			if (StringUtils.isNotBlank(cfgLatitude) && StringUtils.isNotBlank(cfgLongitude)) {
				try {
					homeLongitude = Double.parseDouble(cfgLongitude);
					homeLatitude = Double.parseDouble(cfgLatitude);
				} catch (NumberFormatException ex) {
					throw new ConfigurationException("smhi",
							"Parameters latitude and/or longitude in wrong format. Please check your openhab.cfg!");
				}
			}
			
			String refreshIntervalString = (String) properties.get(CONFIG_KEY_REFRESH);
			if (StringUtils.isNotBlank(refreshIntervalString)) {
				refreshInterval = Long.parseLong(refreshIntervalString);
			}
			
			logger.debug("setProperlyConfigured to true");
			setProperlyConfigured(true);
		}
		else {
			logger.error("Error in configuration in openhab.cfg! Please check contents of smhi config in openhab.cfg!!! " +
					"Check for leading spaces before 'smhi:'!");
		}

	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void bindingChanged(BindingProvider provider, String itemName) {
		SmhiGenericBindingProvider smhiBindingProvider = (SmhiGenericBindingProvider)provider;
		if (smhiBindingProvider.providesBindingFor(itemName))
		{
			logger.debug("SMHI binding changed for item: " + itemName);
		}
		super.bindingChanged(provider, itemName);
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void allBindingsChanged(BindingProvider provider) {
		logger.debug("SMHI all binding changed");
		super.allBindingsChanged(provider);
	}
	
	/**
	 * Start the binding service.
	 */
	@Override
	public void activate() {
		logger.debug("Activating SMHI binding");
		super.activate();
	}

	/**
	 * Shut down the binding service.
	 */
	@Override
	public void deactivate() {	
		logger.debug("Deactivating SMHI binding");
		super.deactivate();
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SmhiDataListV2 {
		
		private Date approvedTime;
		private Date referenceTime;
		private Geometry geometry;
		private List<WeatherDataV2> timeSeries;
		
		/**
		 * approvedTime: "2016-01-18T16:25:14Z"
		 */
		@JsonProperty("approvedTime")
		public Date getApprovedTime() {
			return this.approvedTime;
		}
		
		/**
		 * referenceTime: "2016-01-18T14:00:00Z"
		 */
		@JsonProperty("referenceTime")
		public Date getReferenceTime() {
			return this.referenceTime;
		}
		
		/**
		 * geometry: {
		 *				type: "Point",
		 *				coordinates: [
		 *						[
		 *							16.017767,
		 *							57.999628
		 *						]
		 *				]
		 * }
		 */
		@JsonProperty("geometry")
		public Geometry getGeometry() {
			return this.geometry;
		}
		
		
		/**
		 * timeSeries: [
		 *	 {
		 *		validTime: "2016-01-21T15:00:00Z",
		 *		parameters: [
		 *			{
		 *				name: "msl",
		 *				levelType: "hmsl",
		 *				level: 0,
		 *				unit: "hPa",
		 *				values: [
		 *							1025
		 *						]
		 *			},
		 *	 }
		 * @return
		 */
		@JsonProperty("timeSeries")
		public List<WeatherDataV2> getTimeSeries() {
			return timeSeries;
		}
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Geometry {
		private String type;
		private List<List<Double>> coordinates;
		
		public Geometry() {
			
		}
		
		public Geometry(List<List<Double>> coordinates) {
			super();
			this.coordinates = coordinates;
		}

		/**
		 * type: "Point"
		 */
		@JsonProperty("type")
		public String getType() {
			return this.type;
		}
		
		/**
         * coordinates: [
         *   [
         *     16.017767,
         *     57.999628
         *   ]
         * ]
		 */
		@JsonProperty("coordinates")
		public List<List<Double>> getCoordinates() {
			return this.coordinates;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((coordinates == null) ? 0 : coordinates.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Geometry other = (Geometry) obj;
			if (coordinates == null) {
				if (other.coordinates != null)
					return false;
			} else if (!coordinates.equals(other.coordinates))
				return false;
			return true;
		}
	}
	
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class WeatherDataV2 {
		
		private Date validTime;
		private List<Parameter> parameters;
		private HashMap<String, Parameter> hashParameters = new HashMap<String, Parameter>();
		

		/**
		 * {
		 *		name: "t",
		 *		levelType: "hl",
		 *		level: 2,
		 *		unit: "Cel",
		 *		values: [
		 *					-8.8
		 *				]
		 * }
		 * @return
		 */
		public double getTemperature() {
			return hashParameters.get(SmhiConstants.PARAMETER_TEMPERATURE_JSON).getValues().get(0);
		}

		/**
		 * {
		 *		name: "tstm",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "fraction",
		 *		values: [
		 *					0
		 *				]
		 * }
		 * @return
		 */
		public int getProbabilityThunderstorm() {
			return hashParameters.get(SmhiConstants.PARAMETER_THUNDERSTORM_JSON).getValues().get(0).intValue();
		}

		/**
		 * {
		 * 		name: "tcc_mean",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "octas",
		 *		values: [
		 * 					4
		 *				]
		 * }
		 * 
		 * @return
		 */
		public int getTotalCloudCover() {
			return hashParameters.get(SmhiConstants.PARAMETER_TOTAL_CLOUD_COVER_JSON).getValues().get(0).intValue();
		}

		/**
		 * value between 0-8.
		 * {
		 * 		name: "lcc_mean",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "octas",
		 *		values: [
		 * 					4
		 *				]
		 * }
		 * 
		 * @return
		 */
		public int getLowCloudCover() {
			return hashParameters.get(SmhiConstants.PARAMETER_LOW_CLOUDS_JSON).getValues().get(0).intValue();
		}

		/**
		 * value between 0-8.
		 * {
		 * 		name: "mcc_mean",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "octas",
		 *		values: [
		 * 					0
		 *				]
		 * }
		 * 
		 * @return
		 */
		public int getMediumCloudCover() {
			return hashParameters.get(SmhiConstants.PARAMETER_MEDIUM_CLOUD_COVER_JSON).getValues().get(0).intValue();
		}
		
		/**
		 * value between 0-8.
		 * {
		 * 		name: "hcc_mean",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "octas",
		 *		values: [
		 * 					0
		 *				]
		 * }
		 * 
		 * @return
		 */
		public int getHighCloudCover() {
			return hashParameters.get(SmhiConstants.PARAMETER_HIGH_CLOUD_COVER_JSON).getValues().get(0).intValue();
		}

		/**
		 * {
		 *		name: "r",
		 *		levelType: "hl",
		 *		level: 2,
		 *		unit: "%",
		 *		values: [
		 *					92
		 *				]
		 * }
		 * @return
		 */
		public int getHumidity() {
			return hashParameters.get(SmhiConstants.PARAMETER_HUMIDITY_JSON).getValues().get(0).intValue();
		}

		/**
		 * {
		 * 		name: "pmax",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "kg/m2/h",
		 * 		values: [
		 *					0
		 *				]
		 * }
		 * @return
		 */
		public double getMaxPrecipitation() {
			return hashParameters.get(SmhiConstants.PARAMETER_MAX_PRECIPITATION_JSON).getValues().get(0);
		}
		
		/**
		 * {
		 * 		name: "pmin",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "kg/m2/h",
		 * 		values: [
		 *					0
		 *				]
		 * }
		 * @return
		 */
		public double getMinPrecipitation() {
			return hashParameters.get(SmhiConstants.PARAMETER_MIN_PRECIPITATION_JSON).getValues().get(0);
		}
		
		/**
		 * {
		 *		name: "pmean",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "kg/m2/h",
		 *		values: [
		 *					0
		 *				]
		 * }
		 * @return
		 */
		public double getMeanPrecipitation() {
			return hashParameters.get(SmhiConstants.PARAMETER_MEAN_PRECIPITATION_JSON).getValues().get(0);
		}
		
		/**
		 * 	{
		 *		name: "pmedian",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "kg/m2/h",
		 *		values: [
		 *					0
		 *				]
		 *  }
		 * @return
		 */
		public double getMedianPrecipitation() {
			return hashParameters.get(SmhiConstants.PARAMETER_MEDIAN_PRECIPITATION_JSON).getValues().get(0);
		}
		
		/**
		 * {
		 *		name: "pcat",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "category",
		 *		values: [
		 *					0
		 *				]
		 * }
		 * @return
		 */
		public int getPrecipitationCategory() {
			return hashParameters.get(SmhiConstants.PARAMETER_PRECIPITATION_CATEGORY_JSON).getValues().get(0).intValue();
		}
 
		/**
		 * {
		 *		name: "spp",
		 *		levelType: "hl",
		 *		level: 0,
		 *		unit: "fraction",
		 *		values: [
		 *					-9
		 *				]
		 * }
		 * @return
		 */
		public double getFroozenPrecipitation() {
			return hashParameters.get(SmhiConstants.PARAMETER_FROZEN_PRECIPITATION_JSON).getValues().get(0);
		}

		/**
		 * {
		 * 		name: "msl",
		 *		levelType: "hmsl",
		 *		level: 0,
		 *		unit: "hPa",
		 *		values: [
		 *					1025
		 *				]
		 * }
		 * @return
		 */
		public double getPressure() {
			return hashParameters.get(SmhiConstants.PARAMETER_PRESSURE_JSON).getValues().get(0);
		}

		/**
		 * {
		 *		name: "vis",
		 *		levelType: "hl",
		 *		level: 2,
		 *		unit: "km",
		 *		values: [
		 *					6
		 *				]
		 * }
		 * @return
		 */
		public double getVisibility() {
			return hashParameters.get(SmhiConstants.PARAMETER_VISIBILITY_JSON).getValues().get(0);
		}

		/**
		 * {
         *		name: "wd",
		 *		levelType: "hl",
		 *		level: 10,
		 *		unit: "degree",
		 *		values: [
		 *					293
		 *				]
		 *	}
		 * @return
		 */
		public int getWindDirection() {
			return hashParameters.get(SmhiConstants.PARAMETER_WIND_DIRECTION_JSON).getValues().get(0).intValue();
		}

		/**
		 * {
         * 		name: "gust",
         *   	levelType: "hl",
		 *		level: 10,
		 *		unit: "m/s",
		 *		values: [
		 *					3
		 *				]
		 * }
		 * @return
		 */
		public double getWindGust() {
			return hashParameters.get(SmhiConstants.PARAMETER_WIND_GUST_JSON).getValues().get(0);
		}
		
		/**
		 * 	{
		 *		name: "ws",
		 *		levelType: "hl",
		 *		level: 10,
		 * 		unit: "m/s",
		 *		values: [
		 *					1.6
		 *				]
		 *	}
		 * @return
		 */
		public double getWindVelocity() {
			return hashParameters.get(SmhiConstants.PARAMETER_WIND_VELOCITY_JSON).getValues().get(0);
		}
		
		/**
		 * validTime: "2016-01-21T15:00:00Z"
		 */
		@JsonProperty("validTime")
		public Date getValidTime() {
			return this.validTime;
		}
		
		/**
		 * parameters: [
         *     {
         *        name: "msl",
         *        levelType: "hmsl",
         *        level: 0,
         *        unit: "hPa",
         *        values: [
         *                   1012
         *                ]
         *     },
		 */
		@JsonProperty("parameters")
		public List<Parameter> getParameters() {
			return this.parameters;
		}
		
		public void processData() {
			for (Parameter parameter  : this.parameters) {
				hashParameters.put(parameter.name, parameter);
			}
		}
			
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Parameter {
		private String name;
		private String levelType;
		private int level;
		private String unit;
		private List<Double> values;
		
		/**
		 * name: "t"
		 */
		@JsonProperty("name")
		public String getName() {
			return this.name;
		}
		
		/**
		 * levelType: "hl"
		 */
		@JsonProperty("levelType")
		public String getLevelType() {
			return this.levelType;
		}
		
		/**
		 * levelType: 2
		 */
		@JsonProperty("level")
		public int getLevel() {
			return this.level;
		}
		
		/**
		 * unit: "Cel"
		 */
		@JsonProperty("unit")
		public String getUnit() {
			return this.unit;
		}
		
		/**
		 * values: [
         *           -4.2
         *         ]
		 */
		@JsonProperty("values")
		public List<Double> getValues() {
			return this.values;
		}

	}
		
}

OpenHAB-binding-SMHI
====================

A SMHI weather binding for OpenHab supporting [SMHI API PMP2g](http://www.smhi.se/klimatdata/ladda-ner-data/api-for-pmp-dokumentation-1.76980). 
This binding is based on the work made by Mattias Markehed on https://github.com/ibaton/OpenHAB-binding-SMHI with some quite big code changes including support for latest API version and also
configuration for your home position in openhab.cfg. 
The number of queries needed have also been optimized using cached results per position.

For installation of the binding, please see [Wiki](https://github.com/openhab/openhab/wiki/Bindings).

##Download
[org.openhab.binding.smhi_1.8.1.201601232250.jar](https://drive.google.com/file/d/0B7ldc6wwlfo6NHhfY0hxRDFvVEk/view?usp=sharing)


##Binding Configuration

openhab.cfg file (in the folder '${openhab_home}/configurations').

	################################ SMHI Binding ##########################################
	#
	# The latitude/longitude coordinates of 'home'.
	# smhi:home.latitude=59.222156
	# smhi:home.longitude=18.001565
	# smhi:refresh=1800000

If you configure your home position in openhab.cfg, you need only to refer to the SMHI parameter
in the items file.

Refresh time controls how often the SMHI API will queried, default if not configured in
openhab.cfg is 10 minutes (600000 ms). 

##Item Binding Configuration

In order to bind an item to a SMHI exposed parameter, you need to add some binding information in your item file.

If you have configured your home position in openhab.cfg, you only need to configure the desired SMHI parameter in the items file, see the syntax below:
smhi="parameter"

It is also possible to define the position directly in the items file, the syntax of the configuration is listed below:
smhi="latitude:longitude:parameter"

You can combine both ways of configuring in the items file.

Latitude and latitude for your location can be found using [bing](http://www.bing.com/maps).
Latitude must be between 52.50 and 70.75.
Longitude must be between 2.25 and 38.00. 

Valid parameters:
* *temperature* - Temperature. C.
* *probability_thunderstorm* - Probability of thunderstorm. %.
* *pressure* - Air pressure. hPa.
* *visibility* - Visibility. km.
* *wind_direction* - Wind direction. Degrees.
* *wind_velocity* - Wind velocity. m/s.
* *gust* - Wind gust. m/s
* *humidity* - Relative humidity. %.
* *total_cloud_cover* - Total Cloud coverage. (0-8).
* *high_cloud_cover* - High Cloud coverage. (0-8).
* *medium_cloud_cover* - Medium Cloud coverage. (0-8).
* *low_cloud_cover* - Low Cloud coverage. (0-8).
* *max_precipitation* - Max Precipitation. mm/h.
* *min_precipitation* - Min Precipitation. mm/h.
* *mean_precipitation* - Mean Precipitation. mm/h.
* *median_precipitation* - Median Precipitation. mm/h.
* *froozen_precipitation* - Frozen part of total precipitation. % (-9).
* *precipitation_category* - Precipitation category. (0 no, 1 snow, 2 snow and rain, 3 rain, 4 drizzle, 5, freezing rain, 6 freezing drizzle.)

Some examples: 
```
Number SMHI_Temperature "SMHI Temperature [%.1f C°]" { smhi="temperature" }
Number SMHI_Humidity "SMHI Humidity [%d %%]" { smhi="humidity" } 
Number SMHI_Thunderstorm "SMHI Probability Thunderstorm [%d %%]" { smhi="probability_thunderstorm" }
Number SMHI_Pressure "SMHI Pressure [%.1f hPa]" { smhi="pressure" }
Number SMHI_Visibility "SMHI Visibility [%.1f km]" { smhi="visibility" } 
Number SMHI_WindDirection "SMHI Wind Direction [%d degrees]" { smhi="wind_direction" }
Number SMHI_WindVelocity "SMHI Wind Velocity [%.1f m/s]" { smhi="wind_velocity" }
Number SMHI_Gust "SMHI Wind Gust [%.1f m/s]" { smhi="gust" } 
Number SMHI_TotalCloudCover "SMHI Total Cloud Cover [%d (0-8)]" { smhi="total_cloud_cover" }
Number SMHI_HighCloudCover "SMHI High Cloud Cover [%d (0-8)]" { smhi="high_cloud_cover" }
Number SMHI_MediumCloudCover "SMHI Medium Cloud Cover [%d (0-8)]" { smhi="medium_cloud_cover" }
Number SMHI_LowCloudCover "SMHI Low Cloud Cover [%d (0-8)]" { smhi="low_cloud_cover" }
Number SMHI_MaxPrecipitation "SMHI Max Precipitation [%.1f mm/h]" { smhi="max_precipitation" }
Number SMHI_MinPrecipitation "SMHI Min Precipitation [%.1f mm/h]" { smhi="min_precipitation" }
Number SMHI_MedianPrecipitation "SMHI Median Precipitation [%.1f mmh]" { smhi="median_precipitation" }
Number SMHI_MeanPrecipitation "SMHI Mean Precipitation [%.1f mm/h]" { smhi="mean_precipitation" }
Number SMHI_FroozenPrecipitation "SMHI Froozen Precipitation [%d %%]" { smhi="froozen_precipitation" }
Number SMHI_PrecipitationCategory "SMHI Precipitation Category [%d category]" { smhi="precipitation_category" }
Number SMHI_Temperature_Glava "SMHI Temperature Glava [%.1f C°]" { smhi="59.5255134:12.4744869:temperature" }
```

# Shapefile Simplifier#

Shapefile-Simplfier is a set of utilities to reduce the size of a Shapefile and quickly access an in-memory representation of it with respect to an property of interest.

At Foursquare, we use it to quickly retrieve the timezone of a latitude and longitude: timezone reverse geocoding.  A version of this utility is also a component in the Foursquare [twofishes](ttps://github.com/foursquare/twofishes) geocoder.

This is a Java port of the original Scala code written in 2011, in hopes that it will be more useful to others.

## Example ##
Here is how one might use this utility for timzone reverse geocoding.

1. Find a Shapefile containing timezone data, like [http://efele.net/maps/tz/world/](http://efele.net/maps/tz/world/).

  ```
  curl -LO http://efele.net/maps/tz/world/tz_world.zip
  unzip tz_world.zip
  ```
2. Use the the utility to simplfy the information.  It will divide the map into little rectangular cells.  If all of the features (shapes) in a cell have the same property value (we'll use the `TZID` value), all the shapes within that cell will just be replaced with a simple rectangle.  Water has no `TZID` value, so many coastlines will be simplified away. 

  ```
  ./compileAndRun com.foursquare.geo.shapes.ShapefileSimplifier world/tz_world.shp tz_world_simplified.shp TZID
  ```
  The first parameter is main method of the simplifier.  The next is the source we just unzipped, then the destination, and finally the property of interest, `TZID`.  THe output is itself a valid shapefile, so you can take a look:
  ![timezone simplification](http://f.cl.ly/items/3x291u373t221w2U3U10/simplifier1.gif)
  The borders between different timezones are preserved with their original detail.  In cells where the timezones are the same, or there is only one timezone, the detail has been removed.

3. We'll load this new Shapefile into the memory of our server.  An example server with a commandline interface is provided so you experiment.

  ```
  ./compileAndRun com.foursquare.geo.shapes.SimplifiedShapefileClient tz_world_simplified.shp TZID
  lat,long> 40.74, -74.0
  America/New_York  
  ```
  We can test a more complicated timezone boundary snaking through Kentucky:
  ![kentucky timezone border](http://f.cl.ly/items/2H1e232l36062j2R2A00/Screen%20Shot%202015-03-23%20at%2012.03.10%20AM.png)
  
 ```
  lat,long> 36.182, -84.900
  America/New_York 
  lat,long> 36.182, -84.910
  America/Chicago 
  ```


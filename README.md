# Sidewalks
Utility to generate footpaths for OSM-ways with `sidewalk` tag. 

It iterates through all `sidewalk` ways and finds
nearest left and right node for each iterable node. Then it creates new node on bisector of angle 
{previously processed node; current processed node; nearest node} and so on.

That's the same for crossroads but it has a bit more complicated processing logic.
# Build
JDK 8 is required.

Generate archive with `./gradlew jar` and run it from `build/libs` specifying OSM filename as argument.

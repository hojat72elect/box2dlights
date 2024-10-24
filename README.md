# Box2DLights

[![screenshot](http://img.youtube.com/vi/lfT8ajGbzk0/0.jpg)](http://www.youtube.com/watch?v=lfT8ajGbzk0)

In this example game project we are using a 2D lighting framework that uses [box2d](http://box2d.org/) for raycasting
and OpenGL ES 2.0 for rendering. This library is intended to be used with [libgdx](http://libgdx.com).

## Features

* Arbitrary number of lights
* Gaussian blurred light maps
* Point light
* Cone Light
* Directional Light
* Chain Light [New in 1.3]
* Shadows
* Dynamic/static/xray light
* Culling
* Colored ambient light
* Gamma corrected colors
* Handler class to do all the work
* Query method for testing is point inside of light/shadow

The underlying library offers an easy way to add soft dynamic 2d lights to your physic based games.

If you use Gradle, add the following dependency to your build.gradle file, in the dependencies block of the core
project:

`implementation "com.badlogicgames.box2dlights:box2dlights:1.4"`


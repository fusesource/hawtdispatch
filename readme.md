# ![HawtDispatch][logo]
[logo]: http://hawtdispatch.fusesource.org/images/project-logo.png

## Synopsis

[HawtDispatch][] is a small ( less than 100k ) thread pooling and NIO
handling framework API modeled after the `libdispatch` API. `libdispatch` is
the API that Apple created to power the Grand Central Dispatch (GCD)
technology in OS X. It allows you to more easily develop multi-threaded
applications which can more easily scale to take advantage of all the
processing cores on your machine. At the same time, it's development model
simplifies solving many of the problems that plague multi-threaded NIO
development.

[HawtDispatch]:http://hawtdispatch.fusesource.org

## Resources

* [Developer Guide](http://hawtdispatch.fusesource.org)
* [API Reference](http://hawtdispatch.fusesource.org/maven/1.0/hawtdispatch/apidocs/org/fusesource/hawtdispatch/package-summary.html)

## Building from Source

Prerequisites:

* [Maven >= 2.2.1](http://maven.apache.org/download.html)
* [Java JDK >= 1.6](http://java.sun.com/javase/downloads/widget/jdk6.jsp)

In the this directory, run:

    mvn install


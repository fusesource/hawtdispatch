# ![HawtDispatch](http://hawtdispatch.fusesource.org/images/project-logo.png)
=============================================================================

## [HawtDispatch 1.5](http://hawtdispatch.fusesource.org/blog/releases/release-1-5.html), released 2011-11-29

* HawtDispatch threads are now in a thread group.  Setting the UnchaughtExceptionHandler on one of the HawtDispatch threads set the handler for all the threads in the pool.
* Pass an uncaught exceptions to the UncaughtExceptionHandler configured on the HawtDispatch threads.
* Added UDP example
* Fix race condition that was occurring when a serial dispatch queue was suspended.
* Switch to new scalate version which fixes build problems when on OS X Lion.

## [HawtDispatch 1.4](http://hawtdispatch.fusesource.org/blog/releases/release-1-4.html), released 2011-07-18

* Support ordered EventAggregators

## [HawtDispatch 1.3](http://hawtdispatch.fusesource.org/blog/releases/release-1-3.html), released 2011-06-01

* Added an assertExecuting method to the DispatchQueue interface.
* Protect against exceptions from the event dispatch source handlers.
* Upgrade to Scala 2.9.0-1

## [HawtDispatch 1.2](http://hawtdispatch.fusesource.org/blog/releases/release-1-2.html), released 2011-01-20

* Protect against exceptions from the event dispatch source handlers.
* Adding a new HawtServerSocketChannel and HawtSocketChannel which use scala continuations and hawtdispatch to implement NIO based sockets
* Enriching executors with a `runnable` - enriching dispatch queues with a `repeatAfter` method
* Trimming dead code.
* Inline the dispatch a bit.
* Making dispatch queue interface more consistent with java executors.  renamed dispatchAsync to just plain execute, and renamed dispatchAfter to executeAfter
* Removing the Retained interface from the dispatch objects.  It adds complexity and overhead without providing the much benefit due to the JVM's automatic GC
* Added scala type getter/setters for the a dispatch queue label.
* Protect against ConcurrentModificationException on the selector's selecteKeys.
* Enriched the DispatchSource objects
* Added some doco on the new `!!` method.
* Adjusted paul's auto reset method so that it also returns a future.
* Added await methods to the future since they are easier to spot than the apply methods.
* Support for function, !^, that wraps a partial function in a reset block, thus hiding shift/reset.
* OSGi integration, added activator so that started thread can be shutdown when the bundle is stopped.  Also exposed the Dispatch interface
* Added OSGi metadata to the jars.

## [HawtDispatch 1.1](http://hawtdispatch.fusesource.org/blog/releases/release-1-1.html), released 2011-01-20

* Fix bug where the scala version of getCurrentThreadQueue was returning null
* A Future can trigger a callback once it's completed.
* Scala continuation support added.
* Built against Scala 2.8.1
* The maximum number of executions drained from a serial queue before checking 
  global queues for more work is now configurable via the `hawtdispatch.drains` 
  system property.
* createSerialQueue renamed to createQueue to make the API more consistent.
* Handling the case where a selector key is canceled during registration.
* Timer thread is more efficient now.
* Added a AggregatingExecutor to batch up executions
* You can now hook into getting profiling statistics of the dispatch queues
* Fixed the INTEGER_OR aggregator
* Scala usage simplification, import org.fusesource.hawtdispatch._ instead of 
  org.fusesource.hawtdispatch.ScalaDispatch._ 
* More test cases
* Add getThreadQueue method

## [HawtDispatch 1.0](http://hawtdispatch.fusesource.org/blog/releases/release-1-0.html), released 2010-07-22

* Initial release
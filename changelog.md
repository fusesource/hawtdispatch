# ![HawtDispatch](http://hawtdispatch.fusesource.org/images/project-logo.png)
=============================================================================

## [HawtDispatch 1.17](http://hawtdispatch.fusesource.org/blog/releases/release-1-17.html),  released 2013-05-18

* Add a get method to Future objects.
* Fixing bug where SSL transports were not getting past the SSL handshake (on linux).

## [HawtDispatch 1.16](http://hawtdispatch.fusesource.org/blog/releases/release-1-16.html), released 2013-05-10

* Fixing invalid the metric duration value.
* Do a better job ignoring errors when JMX is not supported by the platform. (Android etc.)
* Fixes issue #9: HawtDispatch does not work on Android 4.0 

## [HawtDispatch 1.15](http://hawtdispatch.fusesource.org/blog/releases/release-1-15.html), released 2013-04-19

* Also include info about how long the metric was monitored for.
* Expose dispatch queue profiling data via JMX.

## [HawtDispatch 1.14](http://hawtdispatch.fusesource.org/blog/releases/release-1-14.html), released 2013-04-09

* Fixes bug where you could end up in a CPU spin when the SSL session closed.
* Added a disabledCypherSuites property to the SSL transports to support disabling some of the supported cypher suites.
* Add a getThread() method to the ThreadDispatchQueue class.

## [HawtDispatch 1.13](http://hawtdispatch.fusesource.org/blog/releases/release-1-13.html), released 2012-12-23

* Upgrade to scala 2.10
* Avoid possible NPE.
* Setup the client SSL transports so that the internal session reuse strategy can be used.

## [HawtDispatch 1.12](http://hawtdispatch.fusesource.org/blog/releases/release-1-12.html), released 2012-09-20

* Custom dispatch sources will now return null after the event has been received to avoid accidentally double processing events.
* Make sure we only return false from the offer method when the transport is also full()
* Add a closeOnCancel option to disable closing the socket when the transport is stopped.
* Rename the SecuredTransport interface to SecuredSession and now both the SSLProtocolCodec and SslTransport implement it.
* You can now handle SSL/TLS encoding/decoding via a wrapping protocol codec.  ProtocolCodec and Transport interfaces needs a couple of adjustments to properly support the co
* Better handling of getting the local host address.
* Protocol codec decoding optimizations.
* Move all the connect logic into the start method.
* Do host name resolutions on a blocking executor to avoid stalling the hawtdispatch threads.
* Resize the read buffer after reading from the channel if to avoid to avoid holding on to large buffers.
* Support changing the socket send/recv buffer size on started transports and servers.
* Do at least a non blocking select when we notice that another thread requested the NIO manager wakeup.  This should allow us to pickup any new IO events that have occurred 
* Dropped the continuations example, added a SSL transport client example

## [HawtDispatch 1.11](http://hawtdispatch.fusesource.org/blog/releases/release-1-11.html), released 2012-05-02

* Support buffer pooling in the abstract protocol codec.
* Adding a TransportAware interface which codecs can implement to be injected with their associated codec.
* Make it easy to configure the client auth mode on the ssl transport server.
* Fixes SSL transport bug where inbound data was not delivered after SSL handshake completed.
* Allow a SslTransportServer to be created just using the constructor.

## [HawtDispatch 1.10](http://hawtdispatch.fusesource.org/blog/releases/release-1-10.html), released 2012-04-06

* Fix assertion error message.
* Switch to using 'Task' abstract base instead of the Runnable interface, in many cases it improves perf by 20% since virtual invocations are cheaper then interface invocation
* Adding a UDP based transport.
* Support configuring the client auth mode.

## [HawtDispatch 1.9](http://hawtdispatch.fusesource.org/blog/releases/release-1-9.html), released 2012-02-27

* Fixes LOW priority global queue was being created with a high thread priority.
* Distribute work load spikes fairly to the worker threads.
* Support updating the transport's dispatch queue.
* Add assertion that catches the following error: queue.setTargetQueue(queue)
* Adding a SecureTransport interface and removing many of the leaky abstractions in the transport package.

## [HawtDispatch 1.8](http://hawtdispatch.fusesource.org/blog/releases/release-1-8.html), released 2012-01-30

* Fixes CPU spin that occurred when a peer disconnected while SSL handshake is in progress.
* Could not create a client initiated SSL connection.

## [HawtDispatch 1.7](http://hawtdispatch.fusesource.org/blog/releases/release-1-7.html), released 2012-01-13

* Cleaning up the transport interfaces. Added an abstract protocol codec that makes it easier to implement them.

## [HawtDispatch 1.6](http://hawtdispatch.fusesource.org/blog/releases/release-1-6.html), released 2011-12-19

* Support using a system property to configure the number of dispatch threads.
* Added a hawtdispatch-transport module which provides nice Transport abstraction for working with Sockets and HawtDispatch.

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
# ![HawtDispatch](http://hawtdispatch.fusesource.org/images/project-logo.png)
=============================================================================

## [HawtDispatch 1.1](http://hawtdb.fusesource.org/maven/1.1), released 2011-01-20

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

## [HawtDispatch 1.0](http://hawtdb.fusesource.org/maven/1.0), released 2010-07-22

* Initial release
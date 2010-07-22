# Usage Guide

* Table of Contents
{:toc}

## The DispatchQueue

The most important objects in the {project_name:} API, are the [DispatchQueue][]
objects.  They are Executor objects which will execute submitted runnable 
objects at a later time.  They come in 2 flavors:

[DispatchQueue]: {api_dir:}/DispatchQueue.html 

* __Concurrent Dispatch Queue__: The tasks submitted to the concurrent dispatch queues will execute 
  concurrently and therefore must be thread safe.  The order of execution of the tasks
  is non deterministic.  There are only 3 concurrent queues shared system wide.  One for
  each priority level and can be accessed using the [`Dispatch.getGlobalQueue`][Dispatch_getGlobalQueue]
  method.
  
  [Dispatch_getGlobalQueue]: {api_dir:}/Dispatch.html#getGlobalQueue(org.fusesource.hawtdispatch.DispatchPriority)
  
  Example:
  {pygmentize_and_compare::}
  -----------------------------
  java: In Java
  -----------------------------
  DispatchQueue queue = getGlobalQueue(HIGH);
  -----------------------------
  scala: In Scala
  -----------------------------
  val queue = getGlobalQueue(HIGH)
  {pygmentize_and_compare}
  
* __Serial Dispatch Queue__: Execute the submitted runnable tasks in FIFO order. A serial dispatch queue will 
  only invoke one runnable at a time, but independent queues may each execute their 
  runnable objects concurrently with respect to each other.  Serial dispatch queues are created
  by the application using the [`Dispatch.createQueue`][Dispatch_createQueue]
  method.  
  
  [Dispatch_createQueue]: {api_dir:}/Dispatch.html#createQueue(java.lang.String)
  
  Example:
  {pygmentize_and_compare::}
  -----------------------------
  java: In Java
  -----------------------------
  DispatchQueue queue = createQueue("My queue");
  -----------------------------
  scala: In Scala
  -----------------------------
  val queue = createQueue("My Queue")
  {pygmentize_and_compare}
  
### Handy imports
The examples in this document assume that you have 
added the following imports:

<div class="wide">
{pygmentize_and_compare::}
-----------------------------
java: In Java
-----------------------------
import org.fusesource.hawtdispatch.*;
import static org.fusesource.hawtdispatch.Dispatch.*;
-----------------------------
scala: In Scala
-----------------------------
import _root_.org.fusesource.hawtdispatch._;
import _root_.org.fusesource.hawtdispatch.ScalaDispatch._;
{pygmentize_and_compare}
</div>

### Submitting Runnable Objects

Once you have a reference to a queue object you can use it to 
perform some asynchronous processing.  The Scala queue object is 
enriched with several helpers to make enqueuing async tasks easier. 
Example:

{pygmentize_and_compare::}
-----------------------------
java: In Java
-----------------------------
queue.execute(new Runnable(){
  public void run() {
    System.out.println("Hi!");
  }
});
-----------------------------
scala: in Scala
-----------------------------
queue {
  System.out.println("Hi!");
}
// or
queue << ^{
  System.out.println("Hi!");
}
// or
^{
  System.out.println("Hi!");
} >>: queue
{pygmentize_and_compare}

The `^{ .... }` block syntax in the Scala example is browed from the GCD.  It produces
a regular Java Runnable object.

## Dispatch Sources

A Dispatch Source is used trigger the execution of task on a queue based on an external event.  They are usually used to integrate
with external IO events from NIO, but you can also use a custom Dispatch Source to coalesce multiple application generated events
into a single event which triggers an async task.

Dispatch sources are initially created in a suspended state.  Once its' created and you have configured it's event handler, you should
call the [`DispatchSource.resume`][DispatchSource_resume] method so that it is executed on the specified queue.  If you later
want to stop processing events for a period of time, call the [`DispatchSource.suspend`][DispatchSource_suspend] method.

[DispatchSource_resume]: {api_dir:}/Suspendable.html#resume()
[DispatchSource_suspend]: {api_dir:}/Suspendable.html#suspend()

### NIO Dispatch Source

NIO integration is accomplished via a [DispatchSource][] object which is
created using the [`Dispatch.createSource`][Dispatch_createSource] method.  You supply it
the [`SelectableChannel`][SelectableChannel] and the operations your interested in receiving events for
like [`OP_READ`][OP_READ] or [`OP_WRITE`][OP_WRITE] and when it's that NIO event is raised, 
it will execute a runnable callback you configure on a dispatch queue 
you specify. {project_name:} takes care of setting up and managing the
NIO selectors and selector keys for you.

[DispatchSource]: {api_dir:}/DispatchSource.html 
[Dispatch_createSource]: {api_dir:}/Dispatch.html#createSource(java.nio.channels.SelectableChannel,%20int,%20org.fusesource.hawtdispatch.DispatchQueue) 
[SelectableChannel]: http://java.sun.com/j2se/1.5.0/docs/api/java/nio/channels/SelectableChannel.html
[OP_READ]: http://java.sun.com/j2se/1.5.0/docs/api/java/nio/channels/SelectionKey.html#OP_READ
[OP_WRITE]: http://java.sun.com/j2se/1.5.0/docs/api/java/nio/channels/SelectionKey.html#OP_WRITE

Example:

<div class="wide">
{pygmentize_and_compare::}
-----------------------------
java: In Java
-----------------------------
SelectableChannel channel = ...
DispatchQueue queue = createQueue()
DispatchSource source = createSource(channel, OP_READ, queue);
source.setEventHandler(new Runnable(){
  public void run() {
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    int count;
    while( (c=channel.read(buffer)) > 0 ) {
      // just dump it to the console
      System.out.write(buffer.array(), buffer.offset(), buffer.position());
    }
  }
});
source.resume();
-----------------------------
scala: In Scala
-----------------------------
val channel:SelectableChannel = ...
val queue = createQueue
val source = createSource(channel, OP_READ, queue)
source.setEventHandler(^{
  val buffer = ByteBuffer.allocate(1024)
  var count=0
  while( (c=channel.read(buffer)) > 0 ) {
    // just dump it to the console
    System.out.write(buffer.array(), buffer.offset(), buffer.position());
  }
});
source.resume
{pygmentize_and_compare}
</div>

### Custom Dispatch Source

A Custom Dispatch Source is used to coalesce multiple application generated events
into a single event which triggers an async task.  By using a Custom Dispatch Source you
reduce the amount of cross thread contention since multiple events generated by one thread
are passed to the processing thread as a single batch.

When you create a custom dispatch source, you provide it an aggregator which controls
how events are coalesced.  The supplied aggregators are:

* __`EventAggregators.INTEGER_ADD`__ : Merges integer events by adding them
* __`EventAggregators.LONG_ADD`__ : Merges long events by adding them
* __`EventAggregators.INTEGER_OR`__ : Merges integer events by bit wise or'ing them
* __`EventAggregators.linkedList()`__ : Merges Object events by adding them to a LinkedList

Event producers can call the `merge(event)` method on the custom dispatch source to 
supply it data.  Calling the merge method will cause the event handler Runnable configured
on the dispatch source to be executed.  When it is executed, it you should use the 
custom dispatch source `getData()` method to access the merged event. The `getData()`
should only be called from the configured event handler. 

<div class="wide">
{pygmentize_and_compare::}
-----------------------------
java: In Java
-----------------------------
final Semaphore done = new Semaphore(1-(1000*1000));

DispatchQueue queue = createQueue();
final CustomDispatchSource<Integer, Integer> source = createSource(EventAggregators.INTEGER_ADD, queue);
source.setEventHandler(new Runnable() {
  public void run() {
    int count = source.getData();
    System.out.println("got: " + count);
    done.release(count);
  }
});
source.resume();

// Produce 1,000,000 concurrent merge events
for (int i = 0; i < 1000; i++) {
  getGlobalQueue().execute(new Runnable() {
    public void run() {
      for (int j = 0; j < 1000; j++) {
        source.merge(1);
      }
    }
  });
}

// Wait for all the event to arrive.
done.acquire();
-----------------------------
scala: In Scala
-----------------------------
val done = new Semaphore(1 - (1000 * 1000))

val queue = createQueue()
val source = createSource(EventAggregators.INTEGER_ADD, queue)
source.setEventHandler(^{
  val count = source.getData()
  println("got: " + count)
  done.release(count.intValue)
});
source.resume();

// Produce 1,000,000 concurrent merge events
for (i <- 0 until 1000) {
  globalQueue {
    for (j <- 0 until 1000) {
      source.merge(1)
    }
  }
}

// Wait for all the event to arrive.
done.acquire()
{pygmentize_and_compare}
</div>

On an 8 core machine you would see output similar to:
{pygmentize:: text}
got: 167000
got: 103000
got: 103000
got: 163000
got: 119000
got: 109000
got: 111000
got: 125000
{pygmentize}

## Restrictions on Executed Runnables

All runnable actions executed asynchronously by {project_name:} should be non-blocking
and avoid waiting on any synchronized objects.  If a blocking call has to performed, it should be done 
asynchronously in a new thread not managed by {project_name:}.

## Common Patterns

### Protecting Mutable State
A common pattern that shows up to use a serial queue to synchronize access to the mutable state of an 
object.  Example:

{pygmentize:: scala}
  class MyCounter {
    val queue = createQueue()
    var counter = 0;
    
    def add(value:Int) = ^{
      counter += value
    } >>: queue
    
    def sub(value:Int) = ^{
      counter -= value
    } >>: queue
  }
{pygmentize}

### Asynchronous Cleanup

On many occasions there is a resource associated with concurrent processing, and it needs to be released/cleaned 
up once the concurrent processing has completed.

This can be easily done by configuring a disposer callback on the dispatch queue or dispatch source.  These object support
being reference counted using the [`retain`][Retained_retain] and [`release`][Retained_release] method calls.  They are initially created with a retain count of 1.  
Once the retain count reaches zero, the disposer runnable is executed on the associated queue.

[Retained_retain]: {api_dir:}/Retained.html#retain()
[Retained_release]: {api_dir:}/Retained.html#release()

The following example tries to simulate a case were multiple concurrent tasks are using a shared resource and once
they finish executing that shared resource gets closed.

{pygmentize:: scala}
val stream:PrintStream = ...
val queue = createQueue()
queue.setDisposer(^{ stream.close })

for( i <- 1 to 10 ) {
  queue.retain
  getGlobalQueue << ^ {
    // Concurrently compute some values and send then to 
    // the stream.
    val value = "Hello "+i
    queue << ^{ stream.println(value) }
    // the stream is closed once the last release executes.
    queue.release
  }
}
queue.release
{pygmentize}

Its' also important to note that the enqueued runnable objects increment the retain counter.  The following
version of the above example also only closes the stream after all the values are sent
to the stream:

{pygmentize:: scala}
val stream:PrintStream = ...
val queue = createQueue()
queue.setDisposer(^{ stream.close })
for( i <- 1 to 10 ) {
  val value = "Hello "+i
  queue << ^{ stream.println(value) }
}
queue.release
{pygmentize}



## References
* [Echo Server Example](http://github.com/chirino/hawtdispatch/blob/master/hawtdispatch-example/src/main/scala/org/fusesource/hawtdispatch/example/EchoServer.scala#L34) Source code for a simple TCP based echo server.
* [STOMP Broker Example](stomp-example.html)  Overview of a more complex networking server example.
* [Java API]({api_dir:}/package-summary.html)
* [Scala API]({scala_api_dir:}/index.html)


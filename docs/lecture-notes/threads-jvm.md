# Threads in the JVM

Threads in the JVM are represented by the [`Thread` class](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Thread.html).

A JVM application can have multiple application threads, in addition to the threads the JVM uses internally.

There are two ways to create a thread:

- Instantiate a `Thread` instance, passing in a [`Runnable`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Runnable.html) instance defining the code the thread will execute. A `Runnable` is a java standard interface with a single `run` method, without arguments, returning `void`, and that doesn't throw any checked exceptions.

- Create a derived class from `Thread` and override the [`run`](<https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Thread.html#run()>). Then create an instance of the derived class.

Create threads remain in the [`NEW`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Thread.State.html#NEW), meaning that they are not ready to run.
To transition a thread into the [`RUNNABLE`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Thread.State.html#RUNNABLE) state, call the [`start`](<https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Thread.html#start()>) method.
Don't mixup the `start` and `run` methods:

- The `start` method is called to transition a thread into the `RUNNING` state.
- The `run` method defines the code the thread will execute.

When a thread is created it is also possible to define other properties, namely:

- A name.
- A [`ThreadGroup`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/ThreadGroup.html).
- The size for the thread's stack.

After a thread is instantiated it is possible to change some of its properties, namely:

- The thread [priority](<https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Thread.html#setPriority(int)>), which will affect the thread scheduling.
- Mark the thread as a [deamon](<https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Thread.html#isDaemon()>) thread or not.

## Daemon threads

Threads in the JVM are divided in two types: _normal_ threads and _deamon_ threads.
This type only has influence in the JVM termination: the JVM will end if all _normal_ threads already [terminated](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Thread.State.html#TERMINATED), even if there are _daemon_ threads still in a non-[`TERMINATED`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Thread.State.html#TERMINATED) state.

When the JVM starts, multiple threads can be created automatically, however only the _main_ thread is non-dameon, i.e. the thread where the `main` method will run.
All other automatically created threads are _daemon_ threads.
When a thread is created it will have the same _daemon_ state as the thread that created it.
This means that all threads created by the main thread will be non-daemon by default, however this property can be changed after a thread is created and before it is started.

Avoid using _deamon_ threads, since the JVM can end without giving those threads the chance for them to properly end, e.g. run resource freeing code.

## Thread interruption

Thread _interruption_ is a mechanism made available by the JVM to provide support for _cancellation_.
It is a way for a thread `T1` to inform another one thread `T2` that the operation being performed by `T2` should not continue.

Consider an Android application where the reaction to an UI event triggers a network communication with an external system.
This I/O operation can potentially block and therefore cannot be made on the UI thread, so a different thread needs to be used.
If in the meanwhile the user leaves the Android activity, we probably want to cancel the pending I/O operation and thread interruption is a way to do it.

Note that an interruption will not transition the target thread to the `TERMINATED` state.
Instead it is just a mechanism to convey a cancellation request.
It is up to the interrupted thread to define what is the reaction to this request.

Threads could use a **properly synchronized** shared boolean for this task:

- The code executing in the target thread would observe this shared boolean periodically and check if it is set.
- An external thread would set this boolean to true as a way to cancel the operation in the target thread.

However threads can block, meaning that during that time they are unable to observe the shared boolean.
The interruption mechanism is therefore a way to terminate blocking method calls, by having these calls end with the throwing of the [`InterruptedException`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/InterruptedException.html).

There are some important aspects to note regarding thread interruption:

- An `InterruptedException` will only be thrown on **some** blocking methods (marked with `throws InterruptedException`). For instance, if a thread is performing a CPU-bound operation and is interrupted, then the `InterruptedException` is not automatically thrown. Checked exceptions can not occur out of thin air. However the exception request will be memorized and an exception will be thrown the next time the target threads does a call of a blocking operation.

- Threads can always check the _interruption status_ by calling the `isInterrupted` getter method. For instance, a thread performing a CPU-bound operation can periodically check this property to verify if a cancellation was requested.

- Unfortunately, not all blocking methods in the JVM are interruption-aware. An important example are [socket](https://docs.oracle.com/javase/7/docs/api/java/net/Socket.html) operations, namely blocking reads. This means that thread interrupts cannot be used as a way to cancel some I/O operations and better alternatives are needed.

- When creating custom synchronizers that have blocking operations, we will want to have the same behaviour as the built-in synchronizers, namely we will want these blocking operations to be interruption-aware. This will introduce some challenges when designing these synchronizers, as we will see.

## Thread termination synchronization

It is possible to wait for the termination of a thread by calling the `join` instance method.
This is a potentially blocking method and therefore can throw `InterruptedException`.

## Deprecated methods

The `Thread` methods `suspend`, `resume`, and `stop` are currently deprecated and should/must no be used.
See the reasons why [here](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/doc-files/threadPrimitiveDeprecation.html).

## Usage examples

See the [examples](../jvm/examples) folder for examples of thread usage.

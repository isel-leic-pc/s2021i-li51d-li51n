# Introduction to threads

## Why have multiple threads?

First, to take advantage of a system having more than one CPU, which is becoming rather common.
As an example, the laptop where I'm writing these words has 4 CPUs and the medium-range smartphone that I carry with me has 8 CPUs.
A way to take advantage of these computational features is to have a different thread executing on each CPU.
This type of design applies well to scenarios where there are enough independent computations to _feed_ these threads.
A good example are server systems (e.g. the information systems with HTTP interfaces that we developed in the LS course) that have the ability to process requests from different clients simultaneously.
In this case the intrisic paralellism, i.e. the independent computations, come from the fact that the systems may be used simultaneously by different users.

The previous paragraph justifies using as many threads as CPUs.
However, does it make sense to use more threads than CPUs?
What could we be gaining with that?
The main answer is simpler code organization by allowing threads to block.

Lets use an example to illustrate what we mean by this.
Consider a typical information system with an HTTP-based interface and backed by a DBMS.
When a request is received, a thread is selected to process that request and starts executing application-level code.
Perhaps this code starts at a servlet, calls an handler/controller, which performs some database operation, eventually through a JDBC helper of some sort.
This database operation implies communication with the external DBMS and, depending on the query complexity and data size, may take hundreds on milliseconds.
During this time, which is almost a figurative eternity for a CPU operating in giga Hertz frequencies, the thread does not have any CPU-bound operation to perform.
It will be a waste of resources to have a CPU allocated for this thread while this database operation is pending.

An option is to reuse this thread to process another request in the meanwhile.
However, this is much easier said than done.
The thread cannot simply return from the function doing the database operation, because there the local state would be lost.
And this state (e.g. the request parameters, the intermediate computations) are required when the database operation completes.
While this is possible, typically it implies structuring applications differently and/or using mechaninsms such as asynchronous methods or coroutines.

A much easier solution is to _block_ the thread, freeing the CPU so that it can host a different thread.
When the database operation finally concludes, the thread will become ready again and elligible to start running by allocating a CPU to it.
It is the fact that threads can block, freeing the CPU where they are executing, that justifies having more threads than CPUs.

Later in this course we will see ways of avoiding _blocking_ threads, by using what are called by asynchronous programming models, typically with the help of the programming language (C#'s asynchronous methods or Kotlin's coroutines).
In this case, instead of blocking, the thread will available to start processing other requests right away.
When absolutely no blocking exists, then we can go back and have exactly as many threads as CPUs.

# Introduction

## Logistics

- The evaluation criteria (term exams, problem sets, minimum grades) are available in Moodle.

- Make sure you are enrolled in Moodle in order to receive all the class notifications.

- Most course resources will be available in or linked from this GitHub repository.
  Looking into the [commits](https://github.com/isel-leic-pc/s2021i-li51d-li51n/commits/main) its a good way of see when and how content is being added.

- The calendar outline, including the problem sets delivery dates are [here](../calendar.md).

- Student requirements:

  - Able to understand and create Java language programs (_LS_ level).
  - Able to understand and create C# language programs (_AVE_ level).
  - Understanding of the x86/x64 assembly language model and of how high-level structured programming concepts are mapped into assembly language (_PSC_ level).
  - Basic knowledge of operation system concepts, namely processes, user and kernel mode, I/O, and thread (_SO_ level).

- See [e-learning](../e-learning.md) for guidelines about the remote lectures.

## Why the Concurrent Programming course?

The Concurrent Programming course primary goal is to provide the knowledge and skills required to create **correct** applications for _multi-threads programming models_.

### Threads are everywhere

Most application level programming models are multi-thread, meaning that application code runs in more than one thread, even if no threads are explicitly created by application code.

As an example, on a servlet-based HTTP server, multiple requests can be handled simultaneously, with the processing of each request being made on a different thread.
On the so called _thread-per-request model_, a new or available thread is selected to host the complete execution of each request.
If the programming model has support for asynchronous operations, such as reactive streams based Spring application, then the execution of a single request may even span multiple distinct threads.

Another example are GUI-based programming models, such as the one defined for Android.
There, a special thread, usually called _UI thread_ or _main thread_, is resposible to host the execution of all GUI related events (e.g. button click handlers).
As a consequence, this thread cannot be used to host operations that take more than some milliseconds, such as requests to external system or CPU-intensive operations.
Making such operations on this thread would mean that the application would become _non-responsive_, i.e. not be able to handle events during these periods.
A way to solve this is to handle these long-term blocking operations on distinct threads, freeing the UI thread to handle GUI events.
This makes application code run in more than one thread, with the associated challenges that this course will help identify and overcome.

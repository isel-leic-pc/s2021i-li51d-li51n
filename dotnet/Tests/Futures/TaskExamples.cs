using System;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Xunit;
using Xunit.Abstractions;

namespace Tests.Futures
{
    public class TaskExamples
    {
        /*
         * Simple blocking example
         */
        [Fact]
        public void HttpGetBlockingExample()
        {
            var client = new HttpClient();
            Log("Before GetAsync");
            var task0 = client.GetAsync("https://httpbin.org/delay/2");
            Log("Before calling task0.Result");
            var response = task0.Result; // This blocks the thread for ~2 seconds, while the response is not available
            Log($"Response={response}");
        }

        /*
         * Using ContinueWith to avoid blocking a thread
         */
        [Fact]
        public void HttpGetContinueWithExample()
        {
            var client = new HttpClient();
            Log("Before GetAsync");
            var task0 = client.GetAsync("https://httpbin.org/delay/2");
            var task1 = task0.ContinueWith(task =>
            {
                Log("Before calling task0.Result");
                var response = task.Result; // This does NOT block the thread, because `task` is already completed
                Log($"Response={response}");
            });

            // Waiting for final task to complete before ending the test
            Log("Waiting for task1 to complete");
            task1.Wait();
        }

        /*
         * Avoid blocking the test thread, by returning a Task
         */
        [Fact]
        public Task HttpGetContinueWithExample2()
        {
            var client = new HttpClient();
            var task0 = client.GetAsync("https://httpbin.org/delay/2");
            var task1 = task0.ContinueWith(task =>
            {
                Log("Before calling task0.Result");
                var response = task.Result; // This does NOT block the thread, because `task` is already completed
                Log($"Response={response}");
            });

            // This way the test will only be considered completed with the returned task completes
            Log("Returning from test method");
            return task1;
        }

        /*
         * Task completing with errors
         */
        [Fact]
        public Task HttpGetWithErrors()
        {
            var client = new HttpClient();
            var task0 = client.GetAsync("https://httpbin2.org/delay/2");
            var task1 = task0.ContinueWith(task =>
            {
                Log($"task status is: {task.Status}");
                Log($"task exception is: {task.Exception.Message}");
                Log("Before calling task0.Result");
                try
                {
                    var response = task.Result; // This will throw because task completed with exception
                    Log($"Response={response}");
                }
                catch (Exception e)
                {
                    Log($"Exception caught: {e.Message}");
                }
            });

            // This way the test will only be considered completed with the returned task completes
            Log("Returning from test method");
            return task1;
        }

        /*
         * Task completing with errors
         */
        [Fact]
        public Task HttpGetWithErrorsAndChaining()
        {
            var client = new HttpClient();
            var task0 = client.GetAsync("https://httpbin2.org/delay/2");
            var task1 = task0.ContinueWith(task =>
            {
                Log($"task status is: {task.Status}");
                Log($"task exception is: {task.Exception.Message}");
                Log("Before calling task0.Result");
                try
                {
                    var response = task.Result; // This will throw because task completed with exception
                    Log($"Response={response}");
                }
                catch (AggregateException e)
                {
                    Log($"Exception caught: {e.InnerException.GetType().Name} - {e.InnerException.Message}");
                }

                return 42;
            });
            var task2 = task1.ContinueWith(task =>
            {
                Log($"task status is: {task.Status}");
                Log($"task result is: {task.Result}");
            });

            // This way the test will only be considered completed with the returned task completes
            Log("Returning from test method");
            return task2;
        }

        [Fact]
        public Task HttpGetWithErrorsAndChaining2()
        {
            var client = new HttpClient();
            var task0 = client.GetAsync("https://httpbin2.org/delay/2");
            var task1 = task0.ContinueWith(task =>
            {
                Log($"task status is: {task.Status}");
                Log($"task exception type is: {task.Exception.InnerException.GetType().Name}");
                Log("Before calling task0.Result");
                return task.Result;
            });
            var task2 = task1.ContinueWith(task =>
            {
                Log($"task status is: {task.Status}");
                Log($"task exception type is: {task.Exception.InnerException.GetType().Name}");
                Log("Before calling task0.Result");
                return task.Result;
            });
            var task3 = task2.ContinueWith(task =>
            {
                Log($"task status is: {task.Status}");
                Log($"task exception type is: {task.Exception.InnerException.InnerException.GetType().Name}");
                Log($"Flattened exception type is: {task.Exception.Flatten().InnerException.GetType().Name}");
            });

            // This way the test will only be considered completed with the returned task completes
            Log("Returning from test method");
            return task3;
        }

        [Fact]
        public Task HttpGetWithAsynchronousChaining()
        {
            var client = new HttpClient();
            Task<HttpResponseMessage> task0 = client.GetAsync("https://httpbin.org/delay/2");
            Task<Task<string>> task1 = task0.ContinueWith(task =>
            {
                Log($"task status is: {task.Status}");
                Log($"task result is: {task.Result}");
                return task.Result.Content.ReadAsStringAsync();
            });
            Task<string> task2 = task1.Unwrap(); // NOT blocking
            var task3 = task2.ContinueWith(task =>
            {
                Log($"task status is: {task.Status}");
                Log($"task result is: {task.Result}");
            });

            // This way the test will only be considered completed with the returned task completes
            Log("Returning from test method");
            return task3;
        }

        [Fact]
        public Task HttpGetWithAsynchronousAndMethodChaining()
        {
            var client = new HttpClient();
            var finalTask = client.GetAsync("https://httpbin.org/delay/2")
                .ContinueWith(task =>
                {
                    Log($"task status is: {task.Status}");
                    Log($"task result is: {task.Result}");
                    return task.Result.Content.ReadAsStringAsync();
                })
                .Unwrap() // NOT blocking
                .ContinueWith(task =>
                {
                    Log($"task status is: {task.Status}");
                    Log($"task result is: {task.Result}");
                });

            // This way the test will only be considered completed with the returned task completes
            Log("Returning from test method");
            return finalTask;
        }

        [Fact]
        public Task HttpGetFollowedByAnotherGet()
        {
            var client = new HttpClient();
            var finalTask = client.GetAsync("https://httpbin.org/delay/1")
                .ContinueWith(task =>
                {
                    Log($"task status is: {task.Status}");
                    Log($"task result is: {task.Result}");
                    return client.GetAsync("https://httpbin.org/delay/1");
                })
                .Unwrap()
                .ContinueWith(task =>
                {
                    Log($"task status is: {task.Status}");
                    Log($"task result is: {task.Result}");
                });

            Log("Returning from test method");
            return finalTask;
        }

        [Fact]
        public Task UsingAsyncMethods()
        {
            /*
             * Notice
             * - the `async` method modifier (private implementation details)
             * - the usage of the operator `await`
             */
            async Task<string> Get()
            {
                var client = new HttpClient();
                Task<HttpResponseMessage> task1 = client.GetAsync("https://httpbin.org/delay/3");
                Log("after GetAsync");
                HttpResponseMessage response = await task1.ConfigureAwait(false); // await: Task<A> -> A, *without* blocking
                Log($"after await, response={response}");
                var task2 = response.Content.ReadAsStringAsync();
                string body = await task2;
                Log($"after await, body={body}");
                return body;
            }

            var task = Get();
            Log("returning from test method");
            return task;

            /*
             * Looking at the log trace, notice:
             * - How the `Get` method returns *before* some of its methods are executed
             * - How the `Get` statements, which are inside the *same* block, are executed in *different* threads
             */
        }

        [Fact]
        public void AsyncMethodsAndExceptions()
        {
            async Task AsyncMethodThatThrows()
            {
                throw new Exception("exception thrown inside async method");
            }
           
            Log("before AsyncMethodThatThrows");
            Task res = AsyncMethodThatThrows();
            Log("after AsyncMethodThatThrows");
            // NO exception is thrown in the line above
            // however the task in the exception state
            Assert.Equal(TaskStatus.Faulted, res.Status);
            var exception  = Assert.Throws<AggregateException>(() => res.Wait());
            Assert.Equal("exception thrown inside async method", exception.InnerException.Message);
        }
        
        [Fact]
        async public Task ParallelExecution()
        {
            async Task ParallelHttpGet()
            {
                Log("ParallelHttpGet begin");
                var client = new HttpClient();
                var x = client.GetAsync("https://httpbin.org/delay/4");
                var y = client.GetAsync("https://httpbin.org/delay/4");
                Log($"status 1 = {(await x).StatusCode}, status 2 = {(await y).StatusCode}");
            }
            async Task SequentialHttpGet()
            {
                Log("SequentialHttpGet begin");
                var client = new HttpClient();
                var x = await client.GetAsync("https://httpbin.org/delay/4");
                var y = await client.GetAsync("https://httpbin.org/delay/4");
                Log($"status 1 = {x.StatusCode}, status 2 = {y.StatusCode}");
            }

            
            await ParallelHttpGet();
            await SequentialHttpGet();
            
        }

        public void ThreadPoolExample()
        {
            Task<int> task = Task.Factory.StartNew(() =>
            {
                Thread.Sleep(5000);
                return 42;
            });
        }

        private readonly ITestOutputHelper _output;

        public TaskExamples(ITestOutputHelper output)
        {
            _output = output;
        }

        private void Log(string s)
        {
            _output.WriteLine("[{0,2}|{1,8}|{2:hh:mm:ss.fff}]{3}",
                Thread.CurrentThread.ManagedThreadId,
                Thread.CurrentThread.IsThreadPoolThread ? "pool" : "non-pool", DateTime.Now,
                s);
        }
    }
}
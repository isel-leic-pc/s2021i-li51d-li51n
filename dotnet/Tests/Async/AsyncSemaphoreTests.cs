using System;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Examples.Async;
using Xunit;
using Xunit.Abstractions;

namespace Tests.Async
{
    public class AsyncSemaphoreTests
    {
        private const int NoTimeout = Timeout.Infinite;
        private const int TimeoutImmediately = 0;
        
        private ITestOutputHelper _output;

        [Fact]
        public void Acquire_with_cancellation_token_already_active()
        {
            const int nOfThreads = 10;
            var sem = new AsyncSemaphore(0);
            var tasks = Enumerable.Range(0, nOfThreads)
                .Select(_ =>
                    Task.Factory.StartNew(async () =>
                    {
                        var cts = new CancellationTokenSource();
                        cts.Cancel();
                        await Assert.ThrowsAsync<TaskCanceledException>(
                            () => sem.AcquireAsync(1, NoTimeout, cts.Token));
                        await Assert.ThrowsAsync<TaskCanceledException>(
                            () => sem.AcquireAsync(1, NoTimeout, cts.Token));
                    }).Unwrap())
                .ToArray();
            Assert.True(Task.WaitAll(tasks, TimeSpan.FromSeconds(1)),
                "test completes timely");
            Assert.True(tasks.All(t => t.Status == TaskStatus.RanToCompletion),
                "all task are successful");
        }
        
        [Fact]
        public void Acquire_with_cancellation_token_already_active_and_zero_timeout()
        {
            const int nOfThreads = 10;
            var sem = new AsyncSemaphore(0);
            var tasks = Enumerable.Range(0, nOfThreads)
                .Select(_ =>
                    Task.Factory.StartNew(async () =>
                    {
                        var cts = new CancellationTokenSource();
                        cts.Cancel();
                        await Assert.ThrowsAsync<TaskCanceledException>(
                            () => sem.AcquireAsync(1, TimeoutImmediately, cts.Token));
                        await Assert.ThrowsAsync<TaskCanceledException>(
                            () => sem.AcquireAsync(1, TimeoutImmediately, cts.Token));
                    }).Unwrap())
                .ToArray();
            Assert.True(Task.WaitAll(tasks, TimeSpan.FromSeconds(1)),
                "test completes timely");
            Assert.True(tasks.All(t => t.Status == TaskStatus.RanToCompletion),
                "all task are successful");
        }
        
        [Fact]
        public void Acquire_with_zero_timeout()
        {
            const int nOfThreads = 10;
            var sem = new AsyncSemaphore(0);
            var cts = new CancellationTokenSource();
            var tasks = Enumerable.Range(0, nOfThreads)
                .Select(_ =>
                    Task.Factory.StartNew(async () =>
                    {
                        Assert.False(await sem.AcquireAsync(1, TimeoutImmediately, cts.Token));
                    }).Unwrap())
                .ToArray();
            Assert.True(Task.WaitAll(tasks, TimeSpan.FromSeconds(1)),
                "test completes timely");
            Assert.True(tasks.All(t => t.Status == TaskStatus.RanToCompletion),
                "all task are successful");
        }

        [Fact]
        public void Acquire_with_cancellation_token_activated_after_Acquire()
        {
            const int nOfThreads = 10;
            const int cancelAfter = 500;
            const int timeoutAfter = 1000;
            
            var sem = new AsyncSemaphore(0);
            var cts = new CancellationTokenSource();
            var tasks = Enumerable.Range(0, nOfThreads)
                .Select(_ =>
                    Task.Factory.StartNew(async () =>
                    {
                        await Assert.ThrowsAsync<TaskCanceledException>(() => sem.AcquireAsync(1, timeoutAfter, cts.Token));
                        await Assert.ThrowsAsync<TaskCanceledException>(() => sem.AcquireAsync(1, timeoutAfter, cts.Token));
                    }).Unwrap())
                .ToArray();
            cts.CancelAfter(cancelAfter);
            Assert.True(Task.WaitAll(tasks, TimeSpan.FromSeconds(1)),
                "test completes timely");
            Assert.True(tasks.All(t => t.Status == TaskStatus.RanToCompletion),
                "all task are successful");
        }

        [Fact]
        public void Acquire_with_cancellation_token_activated_after_Acquire_half_should_succeed()
        {
            const int nOfThreads = 10;
            var sem = new AsyncSemaphore(nOfThreads / 2);
            var cts = new CancellationTokenSource();
            var tasks = Enumerable.Range(0, nOfThreads)
                .Select(_ =>
                    Task.Factory.StartNew(async () => { await sem.AcquireAsync(1, NoTimeout, cts.Token); }).Unwrap())
                .ToArray();
            cts.CancelAfter(500);
            Assert.Throws<AggregateException>(() => Task.WaitAll(tasks, TimeSpan.FromSeconds(1)));
            var successfully = tasks.Count(t => t.Status == TaskStatus.RanToCompletion);
            var cancelled = tasks.Count(t => t.Status == TaskStatus.Canceled);
            Assert.Equal(nOfThreads / 2, successfully);
            Assert.Equal(nOfThreads / 2, cancelled);
        }

        [Fact]
        public void Cyclic_test_asserting_no_extra_releases_and_FIFO()
        {
            const int nOfThreads = 10;
            var timeToRun = TimeSpan.FromSeconds(10);
            var sem = new AsyncSemaphore(1);
            var cts = new CancellationTokenSource();
            var units = 0;
            var acquires = new int[nOfThreads];
            var tasks = Enumerable.Range(0, nOfThreads)
                .Select(tix =>
                    Task.Factory.StartNew(async () =>
                    {
                        while (true)
                        {
                            await sem.AcquireAsync(1, NoTimeout, cts.Token);
                            Assert.Equal(1, Interlocked.Add(ref units, 1));
                            acquires[tix] += 1;
                            await Task.Delay(1);
                            Assert.Equal(0, Interlocked.Add(ref units, -1));
                            sem.Release(1);
                            cts.Token.ThrowIfCancellationRequested();
                        }
                    }).Unwrap())
                .ToArray();
            cts.CancelAfter(timeToRun);
            Assert.Throws<AggregateException>(() =>
                Task.WaitAll(tasks, timeToRun.Add(TimeSpan.FromSeconds(1))));
            var cancelled = tasks.Count(t => t.Status == TaskStatus.Canceled);
            Assert.Equal(nOfThreads, cancelled);
            Assert.Equal(0, units);
            var min = Enumerable.Min(acquires);
            var max = Enumerable.Max(acquires);
            var ratio = 1.0 * (max - min) / max;
            Log($"max=${max}, min=${min}, ratio=${ratio}");
            Assert.True(ratio < 0.05);
            Assert.True(min > 0);
        }
        
        [Fact]
        public void Cyclic_test()
        {
            var doneSource = new CancellationTokenSource();
            var timeToRun = TimeSpan.FromSeconds(10);
            doneSource.CancelAfter(timeToRun);
            var sem = new AsyncSemaphore(1);
            
            async Task SuccessConsumer()
            {
                while (!doneSource.Token.IsCancellationRequested)
                {
                    var res = await sem.AcquireAsync(1, NoTimeout, CancellationToken.None);
                    Assert.True(res);
                    await Task.Delay(1000);
                    sem.Release(1);
                }
            } 
            async Task TimeoutConsumer()
            {
                while (!doneSource.Token.IsCancellationRequested)
                {
                    var res = await sem.AcquireAsync(1, 500, CancellationToken.None);
                    Assert.False(res);
                    Log("timeout");
                }
            } 
            
            const int nOfThreads = 100;
            var tasks = Enumerable.Range(0, nOfThreads)
                .Select(tix =>
                    Task.Factory.StartNew(async () =>
                    {
                        if (tix < 2)
                        {
                            await SuccessConsumer();
                        }
                        else
                        {
                            await TimeoutConsumer();
                        }
                    }).Unwrap())
                .ToArray();
            Task.WaitAll(tasks, timeToRun.Add(TimeSpan.FromSeconds(1)));
        }


        public AsyncSemaphoreTests(ITestOutputHelper output)
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
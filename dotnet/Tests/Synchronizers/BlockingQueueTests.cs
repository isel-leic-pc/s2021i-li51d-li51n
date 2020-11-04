using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using Examples.Synchronizers;
using Examples.Utils;
using Xunit;

namespace Tests.Synchronizers
{
    public class BlockingQueueTests
    {
        private class Value
        {
            private readonly long _value;

            public Value(long value)
            {
                _value = value;
            }

            public long Get()
            {
                return _value;
            }
        }

        private const int NOfThreads = 100;
        private readonly TimeSpan _testDuration = TimeSpan.FromSeconds(60);

        private readonly BlockingQueue<Value> _queue = new BlockingQueue<Value>();
        private readonly Deadline _deadline;
        private long _writeCount = 0;
        private long _readCount = 0;
        private long _interruptCount = 0;
        
        private readonly CountdownEvent _countdownEvent = new CountdownEvent(NOfThreads);

        public BlockingQueueTests()
        {
            _deadline = Deadline.FromTimeout(_testDuration);
        }

        private void Writer()
        {
            var random = new Random();
            while (!_deadline.IsExceeded)
            {
                var value = new Value(random.Next());
                if(_queue.Enqueue(value, TimeSpan.FromMilliseconds(1)))
                {
                    Interlocked.Add(ref _writeCount, value.Get());
                }
            }
        }

        private void Reader()
        {
            while (!_deadline.IsExceeded)
            {
                try
                {
                    var maybeValue = _queue.Dequeue(TimeSpan.FromMilliseconds(1));
                    if (maybeValue != null)
                    {
                        Interlocked.Add(ref _readCount, maybeValue.Get());
                    }
                }
                catch (ThreadInterruptedException e)
                {
                    Interlocked.Increment(ref _interruptCount);
                    _countdownEvent.Signal();
                }
            }
        }
        
        [Fact]
        public void Test()
        {
            var writers = new List<Thread>();
            var readers = new List<Thread>();
            for (var i = 0; i < NOfThreads; ++i)
            {
                var reader = new Thread(Reader);
                reader.Start();
                readers.Add(reader);
                
                var writer = new Thread(Writer);
                writer.Start();
                writers.Add(writer);
            }
            
            var interruptDeadline = Deadline.FromTimeout(_testDuration/2);
            var requestedInterrupts = 0;
            Thread.Sleep(1000);
            while (!interruptDeadline.IsExceeded)
            {
                foreach (var th in readers)
                {
                    th.Interrupt();
                    requestedInterrupts += 1;
                }
                Assert.True(_countdownEvent.Wait(_testDuration), "Missing interrupts");
                _countdownEvent.Reset();
            }
            foreach(var th in readers.Concat(writers))
            {
                Assert.True(th.Join(_testDuration + TimeSpan.FromSeconds(1)), 
                    "Unable to join with threads");
            }

            Value elem;
            while ((elem = _queue.Dequeue(TimeSpan.Zero)) != null)
            {
                _readCount += elem.Get();
            }
            Assert.Equal(requestedInterrupts, _interruptCount);
            Assert.True(_writeCount > 0);
            Assert.Equal(_writeCount, _readCount);
        }
    }
}
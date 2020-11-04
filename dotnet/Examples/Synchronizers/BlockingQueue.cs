using System;
using System.Collections.Generic;
using System.Threading;
using Examples.Utils;

namespace Examples.Synchronizers
{
    public class BlockingQueue<T>
    where T: class
    {
        private class SendRequest
        {
            public bool IsDone = false;
            public readonly Condition Condition;
            public readonly T Element;

            public SendRequest(Condition condition, T element)
            {
                this.Condition = condition;
                this.Element = element;
            }
        }

        private class ReceiveRequest
        {
            public bool IsDone = false;
            public readonly Condition Condition;
            public T? Element = null;

            public ReceiveRequest(Condition condition)
            {
                this.Condition = condition;
            }
        }

        private readonly LinkedList<SendRequest> _sendRequests = new LinkedList<SendRequest>();
        private readonly LinkedList<ReceiveRequest> _receiveRequests = new LinkedList<ReceiveRequest>();
        private readonly DefaultLock _monitor = new DefaultLock();

        public bool Enqueue(T value, TimeSpan timeout)
        {
            _monitor.Lock();
            try
            {
                if (_receiveRequests.Count > 0)
                {
                    var receiver = _receiveRequests.First!;
                    _receiveRequests.RemoveFirst();
                    receiver.Value.Element = value;
                    receiver.Value.IsDone = true;
                    receiver.Value.Condition.Signal();
                    return true;
                }

                if (timeout == TimeSpan.Zero)
                {
                    return false;
                }

                var node = _sendRequests.AddLast(new SendRequest(_monitor.NewCondition(), value));
                var deadline = Deadline.FromTimeout(timeout);
                while (true)
                {
                    TimeSpan remaining = deadline.Remaining();
                    try
                    {
                        node.Value.Condition.Await(remaining);
                    }
                    catch (ThreadInterruptedException)
                    {
                        if (node.Value.IsDone)
                        {
                            Thread.CurrentThread.Interrupt();
                            return true;
                        }

                        _sendRequests.Remove(node);
                        throw;
                    }

                    if (node.Value.IsDone)
                    {
                        return true;
                    }

                    remaining = deadline.Remaining();
                    if (remaining <= TimeSpan.Zero)
                    {
                        _sendRequests.Remove(node);
                        return false;
                    }
                }
            }
            finally
            {
                _monitor.Unlock();
            }
        }

        public T? Dequeue(TimeSpan timeout)
        {
            _monitor.Lock();
            try
            {
                if (_sendRequests.Count > 0)
                {
                    var sender = _sendRequests.First!;
                    _sendRequests.RemoveFirst();
                    sender.Value.IsDone = true;
                    sender.Value.Condition.Signal();
                    return sender.Value.Element;
                }

                if (timeout == TimeSpan.Zero)
                {
                    return null;
                }

                var node = _receiveRequests.AddLast(new ReceiveRequest(_monitor.NewCondition()));
                var deadline = Deadline.FromTimeout(timeout);
                while (true)
                {
                    TimeSpan remaining = deadline.Remaining();
                    try
                    {
                        node.Value.Condition.Await(remaining);
                    }
                    catch (ThreadInterruptedException)
                    {
                        if (node.Value.IsDone)
                        {
                            Thread.CurrentThread.Interrupt();
                            return node.Value.Element;
                        }

                        _receiveRequests.Remove(node);
                        throw;
                    }

                    if (node.Value.IsDone)
                    {
                        return node.Value.Element;
                    }

                    remaining = deadline.Remaining();
                    if (remaining <= TimeSpan.Zero)
                    {
                        _receiveRequests.Remove(node);
                        return null;
                    }
                }
            }
            finally
            {
                _monitor.Unlock();
            }
        }
    }
}
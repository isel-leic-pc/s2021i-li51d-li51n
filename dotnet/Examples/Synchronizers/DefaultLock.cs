using System;
using System.Threading;

namespace Examples.Synchronizers
{
    public interface Condition
    {
        void Await(TimeSpan timeout);
        void Signal();
        void SignalAll();
    }

    public class DefaultLock
    {
        private readonly object _monitor = new object();
        private int _intrinsicConditionAlreadyUsed = 0;

        public void Lock()
        {
            EnterNonInterruptibly(_monitor, out var wasInterrupted);
            if (wasInterrupted)
            {
                Thread.CurrentThread.Interrupt();
            }
        }

        public void Unlock()
        {
            Monitor.Exit(_monitor);
        }

        public Condition NewCondition()
        {
            var current = Interlocked.Exchange(ref _intrinsicConditionAlreadyUsed, 1);
            if (current == 0)
            {
                return new IntrinsicCondition(this);
            }

            return new DefaultCondition(this);
        }

        private void LockNonInterruptibly(out bool wasInterrupted)
        {
            EnterNonInterruptibly(_monitor, out wasInterrupted);
        }

        private static void EnterNonInterruptibly(object monitor, out bool wasInterrupted)
        {
            wasInterrupted = false;
            do
            {
                try
                {
                    Monitor.Enter(monitor);
                    return;
                }
                catch (ThreadInterruptedException e)
                {
                    wasInterrupted = true;
                }
            } while (true);
        }

        private class DefaultCondition : Condition
        {
            private readonly DefaultLock _parentLock;
            private readonly object _conditionMonitor = new object();

            public DefaultCondition(DefaultLock parentLock)
            {
                _parentLock = parentLock;
            }

            public void Await(TimeSpan timeout)
            {
                Monitor.Enter(_conditionMonitor);
                _parentLock.Unlock();
                try
                {
                    Monitor.Wait(_conditionMonitor, timeout);
                }
                finally
                {
                    Monitor.Exit(_conditionMonitor);
                    _parentLock.LockNonInterruptibly(out var wasInterrupted);
                    if (wasInterrupted)
                    {
                        throw new ThreadInterruptedException();
                    }
                }
            }

            public void Signal()
            {
                EnterNonInterruptibly(_conditionMonitor, out var wasInterrupted);
                Monitor.Pulse(_conditionMonitor);
                Monitor.Exit(_conditionMonitor);
                if (wasInterrupted)
                {
                    Thread.CurrentThread.Interrupt();
                }
            }

            public void SignalAll()
            {
                EnterNonInterruptibly(_conditionMonitor, out var wasInterrupted);
                Monitor.PulseAll(_conditionMonitor);
                Monitor.Exit(_conditionMonitor);
                if (wasInterrupted)
                {
                    Thread.CurrentThread.Interrupt();
                }
            }
        }

        private class IntrinsicCondition : Condition
        {
            private readonly DefaultLock _parentLock;

            public IntrinsicCondition(DefaultLock parentLock)
            {
                _parentLock = parentLock;
            }

            public void Await(TimeSpan timeout)
            {
                Monitor.Wait(_parentLock._monitor, timeout);
            }

            public void Signal()
            {
                Monitor.Pulse(_parentLock._monitor);
            }

            public void SignalAll()
            {
                Monitor.PulseAll(_parentLock._monitor);
            }
        }
    }
}
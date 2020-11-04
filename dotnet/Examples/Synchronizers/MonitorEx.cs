using System;
using System.Threading;

namespace Examples.Synchronizers
{
    public class MonitorEx
    {
        public static void Wait(object mlock, object condition, TimeSpan timeout)
        {
            if (mlock == condition)
            {
                Monitor.Wait(mlock, timeout);
                return;
            }
            Monitor.Enter(condition); // can throw ThreadInterruptedException
            Monitor.Exit(mlock);
            try
            {
                Monitor.Wait(condition, timeout); // can throw ThreadInterruptedException
            }
            finally
            {
                Monitor.Exit(condition);
                MonitorEx.EnterNonInterruptibly(mlock, out var wasInterrupted ); // can throw ThreadInterruptedException
                if (wasInterrupted)
                {
                    throw new ThreadInterruptedException();
                }
            }
        }

        public static void Pulse(object condition)
        {
            MonitorEx.EnterNonInterruptibly(condition, out var wasInterrupted );
            Monitor.Pulse(condition);
            Monitor.Exit(condition);
            if (wasInterrupted)
            {
                Thread.CurrentThread.Interrupt();
            }
        }

        public static void PulseAll(object condition)
        {
            MonitorEx.EnterNonInterruptibly(condition, out var wasInterrupted );
            Monitor.PulseAll(condition);
            Monitor.Exit(condition);
            if (wasInterrupted)
            {
                Thread.CurrentThread.Interrupt();
            }
        }

        public static void EnterNonInterruptibly(object mlock, out bool wasInterrupted)
        {
            wasInterrupted = false;
            do
            {
                try
                {
                    Monitor.Enter(mlock);
                    return;
                }
                catch (ThreadInterruptedException e)
                {
                    wasInterrupted = true;
                }
            } while (true);
        }
    }
}
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;

namespace Examples.Async
{
    /*
     * N-ary semaphore with FIFO policy and asynchronous acquisition interface.
     * Based on https://github.com/carlos-martins/isel-leic-pc-s1920v-li51n/blob/master/src/synchs-async/SemaphoreAsync.cs
     */
    public class AsyncSemaphore
    {
        private static readonly Task<bool> TrueTask = Task.FromResult(true);
        private readonly object _lock = new object();

        /*
         * Represents a pending acquisition request.
         */
        class Request : TaskCompletionSource<bool>
        {
            public readonly int RequestedUnits;
            public bool Done;
            
            /*
             * These fields are assigned only once during the AcquireAsync where the Request instance is created
             * and while holding the lock.
             * These fields are accessed to dispose the timer the registration, which can happen in two scenarios
             * - While holding the lock acquired during AcquireAsync, when the cancellation is synchronous (i.e.
             * happens inside the CancellationToken.Register
             * - Without holding the lock *but* after the lock was acquired on an asynchronous cancellation.
             * The fact that the lock was acquired and released by the thread previously ensures the correct
             * visibility of these fields.
             */
            public Timer? Timer;
            public CancellationTokenRegistration? CancellationTokenRegistration ;

            public Request(int requestedUnits)
                // To ensure that completing the task doesn't run the continuations asynchronously.
                :base(TaskCreationOptions.RunContinuationsAsynchronously)
            {
                RequestedUnits = requestedUnits;
            }
        }

        private readonly LinkedList<Request> _requests = new LinkedList<Request>();

        private int _units;
        
        /*
         * The cancellation handlers, created once per semaphore instance.
         * We avoid allocating new handlers for each acquisition call.
         */
        private readonly Action<object?> _cancellationHandler;
        private readonly TimerCallback _timerCallback;

        public AsyncSemaphore(int initialUnits)
        {
            _units = initialUnits;
            // We have the assurance that the object passed to the handler is a LinkedListNode containing a Request
            // due to the way the handlers are registered on an acquisition.
            _cancellationHandler = node => { TryCancel((LinkedListNode<Request>) node!, true); };
            _timerCallback = node => { TryCancel((LinkedListNode<Request>) node!, false); };
        }

        public Task<bool> AcquireAsync(int requestedUnits, int timeoutInMs, CancellationToken ct)
        {
            lock (_lock)
            {
                // fast-path
                if (_requests.Count == 0 && _units >= requestedUnits)
                {
                    _units -= requestedUnits;
                    return TrueTask;
                }

                // async-path
                var request = new Request(requestedUnits);
                var requestNode = _requests.AddLast(request);

                if (timeoutInMs != Timeout.Infinite)
                {
                    request.Timer = new Timer(_timerCallback, requestNode, timeoutInMs, Timeout.Infinite);
                }
                
                Debug.Assert(request.Done == false, 
                    "Request is not complete, because timeout handler is always asynchronous");
                // The cancellation token (CT) handler registration needs to be performed after the timeout handler
                // registration because the CT handler may run synchronously *inside* the Register call
                // and we need to be sure the Timer is disposed in the scenario.
                if (ct.CanBeCanceled)
                {
                    request.CancellationTokenRegistration = ct.Register(_cancellationHandler, requestNode);
                }

                return request.Task;
            }
        }
        
        public void Release(int releasedUnits)
        {
            LinkedList<Request>? nodesToComplete;
            lock (_lock)
            {
                _units += releasedUnits;
                nodesToComplete = ReleaseAllPossible();
            }

            CompleteAll(nodesToComplete);
        }
        
        /*
         * This method removes all queued acquisition requests that can be satisfied,
         * updating the list and available units accordingly.
         * However it does not complete the associated tasks because it is executed inside the lock.
         * Instead, it returns a list with the requests that were satisfied.
         */
        private LinkedList<Request>? ReleaseAllPossible()
        {
            Debug.Assert(Monitor.IsEntered(_lock), "Lock MUST be held");
            
            LinkedList<Request>? queue = null;
            var node = _requests.First;
            while (node != null && _units >= node.Value.RequestedUnits)
            {
                _requests.RemoveFirst();
                _units -= node.Value.RequestedUnits;
                node.Value.Done = true;
                queue ??= new LinkedList<Request>();
                queue.AddLast(node);
                node = _requests.First;
            }

            return queue;
        }
        
        /*
         * This method
         * - Disposes the resources used by a request, namely the timer and CT registration.
         * - Completes the associated task.
         */
        private void CompleteAll(LinkedList<Request>? requestsToRelease)
        {
            if (requestsToRelease != null)
            {
                Debug.Assert(!Monitor.IsEntered(_lock), "Lock must NOT be held");
                foreach (var request in requestsToRelease)
                {
                    DisposeRequest(request, false);
                    request.SetResult(true);
                }
            }
        }

        /*
         * This method *tries* to cancel a pending request.
         * It is "try", because the request may already be completed (success or cancel/timeout) by a different thread.
         * This race is resolved by evaluating the Done field while holding the lock.
         * The first completion to arrive (the lock ensures serialization) marks the request as done,
         * removes it from the queue, disposes the resources and completes the associated task.
         */
        private void TryCancel(LinkedListNode<Request> node, bool isCancelling)
        {
            LinkedList<Request>? releasedRequests;
            lock (_lock)
            {
                if (node.Value.Done)
                {
                    // Request already completed, absolutely nothing else to do
                    return;
                }
                _requests.Remove(node);
                node.Value.Done = true;
                releasedRequests = ReleaseAllPossible();
            }
            // If the code reaches this point, then this cancellation won the race and it is in charge
            // of disposing the request resources and completing the task.
            // This
            // - *Must* be performed outside the lock to avoid deadlocks when disposing the CT.
            // - *Can* be performed outside the lock because only one thread will do that *after* having accessed the lock.
            // The only exception is synchronous cancellation (done inside the CT.Register call). In this case the lock
            // is held, however the registration Dispose doesn't deadlock in that case.
            DisposeRequest(node.Value, isCancelling);
            if (isCancelling)
            {
                node.Value.SetCanceled();
            }
            else
            {
                node.Value.SetResult(false);
            }

            CompleteAll(releasedRequests);
        }
        
        private void DisposeRequest(Request request, bool isCancelling)
        {
            request.Timer?.Dispose();
            if (!isCancelling)
            {
                /*
                 * When the Request dispose is due to a CT registration callback,
                 * then we don't need to dispose that registration
                 * (that is done automatically when the callback is called).
                 */
                request.CancellationTokenRegistration?.Dispose();
            }
        }
    }
}
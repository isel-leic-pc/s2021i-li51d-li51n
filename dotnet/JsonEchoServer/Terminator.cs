using System;
using System.Threading;
using System.Threading.Tasks;

namespace JsonEchoServer
{
    class Terminator
    {
        private int _counter;
        private volatile bool _isShutdown;
        private readonly TaskCompletionSource<object> _tcs = new TaskCompletionSource<object>();

        public IDisposable Enter()
        {
            // This needs to be here to avoid races
            Interlocked.Increment(ref _counter);
            Interlocked.MemoryBarrier();
            if (_isShutdown)
            {
                Leave();
                throw new Exception("cannot enter because it is shutting down");
            }

            return new TerminatorDisposable(this);
        }

        private void Leave()
        {
            Interlocked.Decrement(ref _counter);
            Interlocked.MemoryBarrier();
            if (_isShutdown)
            {
                _tcs.TrySetResult(null);
            }
        }

        public Task Shutdown()
        {
            _isShutdown = true;
            Interlocked.MemoryBarrier();
            if (_counter == 0)
            {
                _tcs.TrySetResult(null);
            }

            return _tcs.Task;
        }

        class TerminatorDisposable : IDisposable
        {
            private readonly Terminator _terminator;

            public TerminatorDisposable(Terminator terminator)
            {
                this._terminator = terminator;
            }

            public void Dispose()
            {
                _terminator.Leave();
            }
        }
    }
}
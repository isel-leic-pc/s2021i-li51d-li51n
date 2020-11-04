using System;

namespace Examples.Utils
{
    public readonly struct Deadline
    {
        private readonly DateTime _deadline;

        private Deadline(DateTime deadline)
        {
            this._deadline = deadline;
        }

        public bool IsExceeded => DateTime.UtcNow >= _deadline;

        public static Deadline FromTimeout(TimeSpan timeSpan)
        {
            return new Deadline(DateTime.UtcNow + timeSpan);
        }

        public TimeSpan Remaining()
        {
            return _deadline - DateTime.UtcNow;
        }
    }
}
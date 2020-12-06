using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace JsonEchoServer
{
    static class Program
    {
        // Represents a request
        public class Request
        {
            public string Method { get; set; }
            public Dictionary<string, string> Headers { get; set; }
            public JObject Payload { get; set; }

            public override string ToString()
            {
                return $"Method: {Method}, Headers: {Headers}, Payload: {Payload}";
            }
        }

        // represents a response
        private class Response
        {
            public int Status { get; set; }
            public Dictionary<string, string> Headers { get; set; }
            public JObject Payload { get; set; }
        }

        private const int Port = 8081;
        private static int _counter;

        public static async Task Main(string[] args)
        {
            var listener = new TcpListener(IPAddress.Loopback, Port);
            var terminator = new Terminator();
            var cts = new CancellationTokenSource();
            var ct = cts.Token;
            Console.CancelKeyPress += (obj, eargs) =>
            {
                Log("CancelKeyPress, stopping server");
                cts.Cancel();
                listener.Stop();
                eargs.Cancel = true;
            };
            listener.Start();
            Log($"Listening on {Port}");
            await using (ct.Register(() => { listener.Stop(); }))
            {
                try
                {
                    while (!ct.IsCancellationRequested)
                    {
                        Log("Accepting new TCP client");
                        var client = await listener.AcceptTcpClientAsync();
                        var id = _counter++;
                        Log($"connection accepted with id '{id}'");
                        Handle(id, client, ct, terminator);
                    }
                }
                catch (Exception e)
                {
                    // await AcceptTcpClientAsync will end up with an exception
                    Log($"Exception '{e.Message}' received");
                }

                Log("waiting shutdown");
                await terminator.Shutdown();
            }
        }

        private static readonly JsonSerializer Serializer = new JsonSerializer();

        private static async void Handle(
            int id,
            TcpClient client,
            CancellationToken cancellationToken,
            Terminator terminator)
        {
            using (terminator.Enter())
            {
                try
                {
                    using (client)
                    {
                        var stream = client.GetStream();
                        var reader = new JsonTextReader(new StreamReader(stream))
                        {
                            // To support reading multiple top-level objects
                            SupportMultipleContent = true
                        };
                        var writer = new JsonTextWriter(new StreamWriter(stream));
                        while (true)
                        {
                            try
                            {
                                // to consume any bytes until start of object ('{')
                                do
                                {
                                    await reader.ReadAsync(cancellationToken);
                                    Log($"advanced to {reader.TokenType}");
                                } while (reader.TokenType != JsonToken.StartObject
                                         && reader.TokenType != JsonToken.None);

                                if (reader.TokenType == JsonToken.None)
                                {
                                    Log($"[{id}] reached end of input stream, ending.");
                                    return;
                                }

                                Log("Reading object");
                                var json = await JObject.LoadAsync(reader, cancellationToken);
                                Log($"Object read, {cancellationToken.IsCancellationRequested}");
                                var request = json.ToObject<Request>();
                                Response response = request != null
                                    ? request.Method switch
                                    {
                                        "ECHO" => Echo(request, json),
                                        "DELAY" => await Delay(request),
                                        _ => new Response() {Status = 405}
                                    }
                                    : new Response {Status = 400};

                                Serializer.Serialize(writer, response);
                                await writer.FlushAsync(cancellationToken);
                            }
                            catch (JsonReaderException e)
                            {
                                Log($"[{id}] Error reading JSON: {e.Message}, ending");
                                var response = new Response
                                {
                                    Status = 400,
                                };
                                Serializer.Serialize(writer, response);
                                await writer.FlushAsync(cancellationToken);
                                // close the connection because an error may not be recoverable by the reader
                                return;
                            }
                            catch (Exception e)
                            {
                                Log($"[{id}] Exception: {e.Message}, ending");
                                return;
                            }
                        }
                    }
                }
                finally
                {
                    Log($"Ended connection {id}");
                }
            }
        }

        private static Response Echo(Request request, JObject json)
        {
            return new Response
            {
                Status = 200,
                Payload = json
            };
        }

        private static async Task<Response> Delay(Request request)
        {
            var delayString = request.Headers?["timeout"] ?? "1000";
            if (!int.TryParse(delayString, out var delay))
            {
                return new Response
                {
                    Status = 400
                };
            }

            await Task.Delay(delay);
            return new Response
            {
                Status = 200
            };
        }

        private static void Log(string s)
        {
            Console.Out.WriteLine("[{0,2}|{1,8}|{2:hh:mm:ss.fff}]{3}",
                Thread.CurrentThread.ManagedThreadId,
                Thread.CurrentThread.IsThreadPoolThread ? "pool" : "non-pool", DateTime.Now,
                s);
        }
    }
}
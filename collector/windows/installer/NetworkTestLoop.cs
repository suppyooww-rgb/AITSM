using System.Diagnostics;
using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace AITSMCollector;

internal sealed record NetworkCommand(string Id, int TransferBytes);

internal static class NetworkTestLoop
{
    public static async Task Run(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var config = Program.LoadConfig();
                using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(45) };
                http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", config.Token);
                http.DefaultRequestHeaders.AcceptEncoding.ParseAdd("identity");
                var root = config.BaseUrl.TrimEnd('/') + "/api/monitoring/collector/network-tests";
                using var pending = await http.GetAsync(root + "/next", token);
                if (pending.StatusCode != HttpStatusCode.NoContent)
                {
                    pending.EnsureSuccessStatusCode();
                    var job = JsonSerializer.Deserialize<NetworkCommand>(await pending.Content.ReadAsStringAsync(token), Program.JsonOptions())!;
                    // Only use the fixed authenticated portal origin, never a job-provided URL.
                    if (!Guid.TryParse(job.Id, out _) || job.TransferBytes != 1_048_576) throw new InvalidOperationException("Invalid network test command");
                    await Execute(http, root, job, token);
                }
            }
            catch (OperationCanceledException) when (token.IsCancellationRequested) { break; }
            catch (Exception error) { Program.Log("Network test polling failed: " + error.GetType().Name); }
            try { await Task.Delay(TimeSpan.FromSeconds(15), token); } catch (OperationCanceledException) { break; }
        }
    }

    private static async Task Execute(HttpClient http, string root, NetworkCommand job, CancellationToken token)
    {
        object result;
        try
        {
            var latency = new List<double>();
            var timer = new Stopwatch();
            for (var i = 0; i < 3; i++)
            {
                timer.Restart();
                using var ping = await http.GetAsync($"{root}/{job.Id}/ping", token);
                ping.EnsureSuccessStatusCode();
                await ping.Content.ReadAsByteArrayAsync(token);
                latency.Add(timer.Elapsed.TotalMilliseconds);
            }
            timer.Restart();
            using var download = await http.GetAsync($"{root}/{job.Id}/download", HttpCompletionOption.ResponseHeadersRead, token);
            download.EnsureSuccessStatusCode();
            var bytes = await download.Content.ReadAsByteArrayAsync(token);
            var downloadSeconds = timer.Elapsed.TotalSeconds;
            if (bytes.Length != job.TransferBytes) throw new InvalidOperationException("Incomplete download probe");
            var payload = RandomNumberGenerator.GetBytes(job.TransferBytes);
            using var content = new ByteArrayContent(payload);
            content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
            timer.Restart();
            using var upload = await http.PostAsync($"{root}/{job.Id}/upload", content, token);
            upload.EnsureSuccessStatusCode();
            using var ack = JsonDocument.Parse(await upload.Content.ReadAsStringAsync(token));
            var uploadSeconds = timer.Elapsed.TotalSeconds;
            if (ack.RootElement.GetProperty("receivedBytes").GetInt32() != payload.Length) throw new InvalidOperationException("Incomplete upload probe");
            result = new { id = job.Id, downloadSeconds, uploadSeconds, latencyMs = latency.Order().ElementAt(1) };
        }
        catch (OperationCanceledException) when (token.IsCancellationRequested) { throw; }
        catch (Exception error)
        {
            // Server receives a stable error classification without URLs, headers or credentials.
            result = new { id = job.Id, error = "Network probe failed: " + error.GetType().Name };
        }
        using var body = new StringContent(JsonSerializer.Serialize(result, Program.JsonOptions()), Encoding.UTF8, "application/json");
        using var response = await http.PostAsync(root + "/result", body, token);
        response.EnsureSuccessStatusCode();
        Program.Log("On-demand network test completed.");
    }
}

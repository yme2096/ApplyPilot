# Optional local AI with Ollama

ApplyPilot uses a small provider interface and supports Ollama as its first provider. AI is disabled by default. No API key, paid API, cloud model, or automatic model download is used.

## Install and select a local model

1. Install Ollama for Windows from the [official download page](https://ollama.com/download/windows), or run its official PowerShell installer command: `irm https://ollama.com/install.ps1 | iex`.
2. In a new PowerShell window, install the requested model yourself: `ollama pull qwen2.5:3b`. This downloads about 1.9 GB. ApplyPilot never runs Ollama's model-pull API or downloads a model.
3. Confirm installation with `ollama list` and confirm the local API responds with `(Invoke-RestMethod http://localhost:11434/api/tags).models | Select-Object name`.

These installation and model-download commands are documented for you to run; they were not run on this computer.

Ollama's local chat endpoint is `POST /api/chat`; its response contains the assistant message content. ApplyPilot requests JSON output through this local API. See the [official chat API documentation](https://docs.ollama.com/api/chat).

## Configure ApplyPilot

Set these variables in `.env`:

```dotenv
LLM_PROVIDER=ollama
OLLAMA_MODEL=qwen2.5:3b
LLM_TIMEOUT_MS=30000
```

For an API started directly on Windows, set:

```dotenv
OLLAMA_BASE_URL=http://localhost:11434
```

For a Docker Compose API container, use Docker Desktop's host gateway:

```dotenv
OLLAMA_BASE_URL=http://host.docker.internal:11434
```

The backend accepts only local Ollama addresses (`localhost`, loopback, or `host.docker.internal`). Remote model endpoints are rejected so this adapter cannot make paid cloud calls. The optional Compose override defaults to `host.docker.internal` with `LLM_PROVIDER=none` and an empty model.

The existing `docker-compose.yml` is unchanged. To opt in to Compose environment pass-through after setting the variables, start only the API with the separate override:

```powershell
docker compose -f docker-compose.yml -f docker-compose.ollama.yml up -d api
```

This recreates only the API container with the configured environment. It adds no service or volume and makes no database changes. I have not applied this override to your running stack.

For a host-run API, set the environment variables in the API process as usual. No database migration or volume change is needed.

The default model configuration is `qwen2.5:3b`, but `LLM_PROVIDER=none` keeps AI off until you opt in. Override `OLLAMA_MODEL` with another locally installed model tag if desired. `OLLAMA_BASE_URL` is configurable; the provider only accepts local hosts.

## Behavior and privacy

- With `LLM_PROVIDER=none` or an empty `OLLAMA_MODEL`, saved-job prompts use deterministic templates and answer-library editing stays manual.
- If Ollama is stopped, the configured model is missing, the request times out, or the response is malformed, job prompts fall back to templates. The answer-draft button shows a controlled message and leaves manual writing available.
- For answer drafts, ApplyPilot sends the question and only the exact excerpts you selected from your own resume. It asks the model to use only those facts. The result is a draft, never saved automatically; the UI tells you to check every claim and edit before saving.
- For job questions, the local model receives the saved job title, company, skills, and a bounded excerpt of the job description. Job-page text is treated as untrusted data.
- The local adapter uses JSON mode and validates the response structure and cited evidence. This reduces unsupported output but does not replace your review.
- The API key variable is not required or used. Do not select an Ollama cloud model; this integration is intentionally limited to local endpoints.

## Verify

The backend provider tests use a temporary local stub; they cover disabled mode, request format, missing model, timeout, malformed output, and rejection of remote hosts. No live Ollama call is made by the test suite. To test an actual evidence-based answer, configure the local provider, open Answer Library, choose a question and a short exact resume excerpt, click **Draft with local AI**, check the cited source/version and excerpt, edit the draft, and save only after review. The editor remains available when AI is disabled or unavailable. No model is installed or downloaded by this project.

For a direct, non-candidate-data generation smoke test after installation, run:

```powershell
$body = @{ model = 'qwen2.5:3b'; stream = $false; messages = @(@{ role = 'user'; content = 'Reply with JSON only: {"ok":true}' }); format = 'json' } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri 'http://localhost:11434/api/chat' -ContentType 'application/json' -Body $body
```

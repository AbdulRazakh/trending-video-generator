# Trending Video Generator

## Complete Developer Guide

**Spring Boot 3 · Java 17 · NewsAPI · Groq · Ollama · Edge-TTS · Unsplash · FFmpeg**
**Version 2.0 | April 2026**

---

## 1. Project Overview

Trending Video Generator is a fully automated AI-powered video generation system that:

- Fetches real trending news articles from **NewsAPI** (structured, clean content)
- Groups and ranks articles by quality score and recency
- Generates professional scripts using **Groq API** (llama3-70b, fast and free)
- Converts narration to speech using **Edge-TTS** (Microsoft, no API key needed)
- Fetches contextual background images from **Unsplash API**
- Produces vertical 9:16 short-form MP4 videos using **FFmpeg** with Ken Burns pan/zoom effects

Ready for YouTube Shorts, Instagram Reels, and TikTok.

---

### Pipeline Summary

```
NewsAPI → Topic Ranking → Groq Script → Edge-TTS Audio → 
Unsplash Image → FFmpeg Ken Burns → Final 9:16 MP4
```

---

### Execution Modes

- Runs automatically every **6 hours** via scheduler
- Can be triggered manually via **REST API**
- Stores metadata in **H2 file database** (persists across restarts)

---

## 2. Software and Tools Required

| Tool | Cost | Purpose |
|------|------|---------|
| Java 17 (JDK) | Free | Spring Boot runtime |
| Maven 3.8+ | Free | Build tool (included via mvnw wrapper) |
| Python 3.8+ | Free | Required by edge-tts CLI |
| edge-tts | Free | Natural voice narration, no API key |
| FFmpeg | Free | Video processing, Ken Burns effect, scene concat |
| Ollama + llama3.2:1b | Free | Local LLM fallback when Groq is unavailable |
| NewsAPI | Free tier | Structured news articles — 100 req/day free |
| Groq API | Free tier | Fast LLM script generation — 6000 tokens/min free |
| Unsplash API | Free tier | Background images — 50 req/hour free |

> NewsAPI, Groq, and Unsplash all have free tiers. No credit card required for any of them.

---

## 3. Installation Guide

### 3.1 Java 17

Download from https://adoptium.net and install. Verify:

```cmd
java -version
```

Expected output: `openjdk version "17.x.x"`

---

### 3.2 Python + Edge-TTS

Download Python from https://python.org — check **Add Python to PATH** during install.

```cmd
pip install edge-tts
```

Find the full path to the executable (needed for application.yml):

```cmd
pip show edge-tts
```

Look at the `Location:` line. The executable is at `...\Scripts\edge-tts.exe`.

Test it works:

```cmd
edge-tts --text "Hello world" --write-media test.mp3
```

---

### 3.3 FFmpeg

Download from https://ffmpeg.org/download.html — get the Windows build.
Extract to a folder such as `C:\Sham\trending_video_generator\ffmpeg\`

Test it works:

```cmd
ffmpeg -version
```

---

### 3.4 Ollama (Local LLM Fallback)

Download from https://ollama.com. After installing, pull the small model:

```cmd
ollama pull llama3.2:1b
ollama list
```

> Ollama is used as fallback only when Groq API is unavailable or rate-limited.
> llama3.2:1b needs ~1.5GB RAM. If you have 8GB+ RAM, use `llama3` for better quality.

---

### 3.5 Get Free API Keys

**NewsAPI** (replaces RSS feeds — structured full article content):
1. Go to https://newsapi.org and create a free account
2. Copy your API key from the dashboard
3. Paste it into `application.yml` at `app.newsapi.apiKey`

**Groq API** (replaces Ollama as primary LLM — faster and more reliable):
1. Go to https://console.groq.com and create a free account
2. Click API Keys → Create new key
3. Paste it into `application.yml` at `app.groq.apiKey`

**Unsplash** (background images):
1. Go to https://unsplash.com/developers
2. Create a free account and click New Application
3. Copy the Access Key (Secret Key is NOT needed)
4. Paste into `application.yml` at `app.unsplash.accessKey`

---

## 4. Configuration (application.yml)

```yaml
server:
  port: 7070

spring:
  datasource:
    url: jdbc:h2:file:./data/trendsdb    # file-based, persists across restarts
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
  h2:
    console:
      enabled: true
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB

app:
  newsapi:
    apiKey: YOUR_NEWSAPI_KEY_HERE
    baseUrl: https://newsapi.org/v2
    pageSize: 10
    language: en
    categories: technology,business,science,health,general

  article:
    maxPerFeed: 10
    minContentLength: 150              # skip articles shorter than this

  ranking:
    topTopics: 3                       # number of videos to generate per run

  video:
    outputDir: C:/Sham/trending_video_generator/video-output
    workspaceDir: C:/Sham/trending_video_generator/video-workspace
    ffmpegPath: C:/Sham/trending_video_generator/ffmpeg/.../bin/ffmpeg.exe

  groq:
    apiKey: YOUR_GROQ_API_KEY_HERE
    baseUrl: https://api.groq.com/openai/v1
    model: llama3-70b-8192

  ollama:
    baseUrl: http://localhost:11434
    model: llama3.2:1b                 # fallback when Groq unavailable
    timeoutSeconds: 120

  unsplash:
    accessKey: YOUR_UNSPLASH_ACCESS_KEY

  edgetts:
    voice: en-US-AriaNeural
    rate: "+0%"
    volume: "+0%"
    executablePath: C:/Users/YOUR_USERNAME/AppData/Local/Programs/Python/Python310/Scripts/edge-tts.exe
```

> Use forward slashes `/` in all Windows paths inside application.yml to avoid YAML escape issues.

---

## 5. All Java Classes Reference

### Entry Point

| Class | Purpose |
|-------|---------|
| `TrendingVideoGeneratorApplication` | Spring Boot main class — starts the application |

---

### Configuration

| Class | Purpose |
|-------|---------|
| `AppProperties` | Maps all `app.*` values from application.yml to Java fields |
| `AppBeansConfig` | Creates the `RestTemplate` Spring bean |

---

### Controller and Scheduler

| Class | Purpose |
|-------|---------|
| `TrendingVideoController` | REST endpoints — POST /run and GET /test |
| `VideoScheduler` | Triggers the pipeline automatically every 6 hours |

---

### Core Pipeline

| Class | Purpose |
|-------|---------|
| `TrendingVideoPipelineService` | Orchestrates the full pipeline — calls all services in order |

---

### Content Ingestion (NEW in v2)

| Class | Purpose |
|-------|---------|
| `NewsApiService` | Fetches structured articles from NewsAPI by category — replaces RssFeedService |
| `TopicRankingService` | Scores articles by content length and recency, selects top N |
| `StoryMergeService` | Merges all articles for a topic into one narrative for the LLM |

---

### Script Generation (UPGRADED in v2)

| Class | Purpose |
|-------|---------|
| `ScriptGeneratorService` | Calls Groq (primary) or Ollama (fallback) to generate VideoScriptJson |
| `ScriptValidationService` | Validates all required fields using Jakarta Bean Validation |

---

### Scene Assembly

| Class | Purpose |
|-------|---------|
| `SceneAssemblyService` | Per-scene coordinator: calls EdgeTTS + Unsplash + FFmpeg for each scene |
| `EdgeTtsService` | Runs edge-tts CLI to convert narration text to MP3 audio |
| `UnsplashService` | Searches Unsplash by imageHints keywords, downloads portrait photo |

---

### Video Assembly

| Class | Purpose |
|-------|---------|
| `VideoAssemblyService` | Concatenates all scene MP4 clips into final video using FFmpeg concat |
| `VideoValidationService` | Verifies output file exists, is over 1KB, and has .mp4 extension |

---

### Upload and Persistence

| Class | Purpose |
|-------|---------|
| `PortalUploadService` | POSTs final MP4 to upload endpoint as multipart/form-data |
| `GeneratedVideoRepository` | JPA repository for saving GeneratedVideo records to H2 |

---

### DTOs and Entities

| Class | Purpose |
|-------|---------|
| `FeedArticle` | One article: source, title, link, summary, fullText, publishedAt, keywords, category |
| `TopicGroup` | Groups related articles under one topic string with quality score |
| `VideoScriptJson` | Full video script: title, language, narrationStyle, list of SceneJson |
| `VideoScriptJson.SceneJson` | One scene: heading, narration, visualPrompt, durationSeconds, imageHints |
| `PortalUploadResponse` | Upload API response: videoId, status, url |
| `GeneratedVideo` | JPA entity: topicTitle, scriptJson, videoPath, uploadStatus, portalUrl, createdAt |

---

## 6. How to Run Locally

### Step 1 — Start Ollama (fallback LLM)

Open a terminal and run:

```cmd
ollama serve
```

Leave this running. Verify it is up:

```cmd
curl http://localhost:11434/api/tags
```

---

### Step 2 — Configure application.yml

Make sure these values are filled in with your actual keys and paths:

- `app.newsapi.apiKey` — from newsapi.org
- `app.groq.apiKey` — from console.groq.com
- `app.unsplash.accessKey` — from unsplash.com/developers
- `app.video.ffmpegPath` — full path to ffmpeg.exe
- `app.video.outputDir` — folder where final MP4s will be saved
- `app.video.workspaceDir` — temp folder for intermediate files
- `app.edgetts.executablePath` — full path to edge-tts.exe

---

### Step 3 — Build and Run

Open Command Prompt in the project root folder:

```cmd
.\mvnw clean package -DskipTests
.\mvnw spring-boot:run
```

App starts on port 7070. You should see:

```
Started TrendingVideoGeneratorApplication in X.XXX seconds
```

---

### Step 4 — Trigger the Pipeline

```cmd
curl -X POST http://localhost:7070/api/trends-video/run
```

The pipeline runs for 2-5 minutes depending on how many topics are configured.

---

### Step 5 — Find Your Videos

```
C:\Sham\trending_video_generator\video-output\
```

Files are named `video_<uuid>.mp4`. Each video is 9:16 portrait format (1080x1920), 30-40 seconds long.

---

## 7. API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | /api/trends-video/test | Health check — returns "Controller is working" |
| POST | /api/trends-video/run | Triggers full pipeline immediately |
| GET | /h2-console | H2 database browser — view GeneratedVideo records |
| GET | /actuator/health | Spring Actuator health check |

---

## 8. Pipeline Execution Steps

1. **NewsApiService** — fetches articles from NewsAPI across all configured categories
2. **TopicRankingService** — scores each article by content length and recency, picks top N
3. **StoryMergeService** — merges articles into narrative text for the LLM
4. **ScriptGeneratorService** — calls Groq API (or Ollama fallback) to write a 3-scene VideoScriptJson
5. **ScriptValidationService** — validates all required fields using Jakarta Bean Validation
6. **SceneAssemblyService** — for each scene: generates audio (Edge-TTS), downloads image (Unsplash), renders Ken Burns clip (FFmpeg)
7. **VideoAssemblyService** — concatenates all scene clips into one final 9:16 MP4
8. **VideoValidationService** — verifies the output file is valid
9. **GeneratedVideoRepository** — saves title, script, video path, and status to H2 database

---

## 9. Script Generation Logic

`ScriptGeneratorService` tries three approaches in order:

**1. Groq API (primary)** — `llama3-70b-8192` model, response_format: json_object forces valid JSON output every time. Fast (~1-2 seconds). Free tier: 6000 tokens/minute.

**2. Ollama (fallback)** — `llama3.2:1b` running locally. Used when Groq rate limit is hit or API is unavailable. Slower (~40-60 seconds) but works offline.

**3. Direct script (final fallback)** — builds narration directly from article sentences with no LLM. Always produces valid content. Used when article content is too short for LLM or both APIs fail.

---

## 10. Common Errors and Fixes

| Error | Fix |
|-------|-----|
| `model 'llama3' not found` | Run `ollama pull llama3.2:1b` and update model name in application.yml |
| `model requires more memory` | Use `llama3.2:1b` — it only needs 1.5GB RAM |
| `Cannot run program 'edge-tts'` | Set `edgetts.executablePath` to the full path of edge-tts.exe |
| `No articles fetched` | Check `app.newsapi.apiKey` is set correctly in application.yml |
| `Groq 401 Unauthorized` | Check `app.groq.apiKey` is correct |
| `FFmpeg fontweight: Option not found` | Remove `fontweight` from drawtext filter — it does not exist in FFmpeg |
| `NullPointerException in SceneAssembly` | Check `app.video.workspaceDir` is set in application.yml |
| `413 MaxUploadSizeExceeded` | Add `spring.servlet.multipart.max-file-size: 500MB` to application.yml |
| `No Unsplash results` | Dark fallback background used automatically — this is expected for generic keywords |
| `FFmpeg output file error` | Make sure outputDir path has no spaces or use quoted paths |

---

## 11. Version History

### v2.0 — April 2026

- **Replaced RssFeedService with NewsApiService** — structured articles with real content, no nav menu garbage
- **Added Groq API as primary LLM** — llama3-70b produces clean JSON every time, replaces unreliable llama3.2:1b for script generation
- **Improved TopicRankingService** — scores articles by content quality and recency instead of first-4-words grouping
- **Three-tier script fallback** — Groq → Ollama → Direct from article sentences (never fails)
- **Fixed JSON sanitization** — robust apostrophe and quote handling for Ollama output
- **H2 file database** — persists video metadata across restarts (was in-memory)
- **FFmpeg escaping fixes** — special characters in narration no longer break video assembly

### v1.0 — March 2026

- Initial release with RSS feed ingestion
- Ollama (llama3.2:1b) for script generation
- Edge-TTS for voice narration
- Unsplash for background images
- FFmpeg Ken Burns effect for video rendering

---

## 12. Planned Upgrades

- **Wav2Lip** — realistic talking avatar face overlaid on each scene (in progress)
- **YouTube Shorts auto-upload** — YouTube Data API v3 integration
- **Instagram Reels auto-post** — Instagram Graph API
- **PostgreSQL** — replace H2 for production deployment
- **Admin dashboard** — preview and approve videos before they go live
- **Category-based visual styles** — different color themes per news category
- **ElevenLabs upgrade** — premium voice quality when budget allows

---

## 13. Architecture Note

All AI and media services are modular and independently swappable:

- Replace **Groq → Claude API** by changing `ScriptGeneratorService.callGroq()`
- Replace **Edge-TTS → ElevenLabs** by changing `EdgeTtsService`
- Replace **Unsplash → Pexels or Getty** by changing `UnsplashService`
- Replace **Ollama → any local LLM** by changing the model name in application.yml

The `TrendingVideoPipelineService` orchestrator never changes when swapping providers.

---

## 14. Project Structure

```
src/
└── main/
    ├── java/com/agent/video/generator/trendsvideo/
    │   ├── TrendingVideoGeneratorApplication.java
    │   ├── config/
    │   │   ├── AppProperties.java
    │   │   └── AppBeansConfig.java
    │   ├── controller/
    │   │   └── TrendingVideoController.java
    │   ├── scheduler/
    │   │   └── VideoScheduler.java
    │   ├── dto/
    │   │   ├── FeedArticle.java
    │   │   ├── TopicGroup.java
    │   │   ├── VideoScriptJson.java
    │   │   └── PortalUploadResponse.java
    │   ├── entity/
    │   │   └── GeneratedVideo.java
    │   ├── repository/
    │   │   └── GeneratedVideoRepository.java
    │   └── service/
    │       ├── TrendingVideoPipelineService.java
    │       ├── NewsApiService.java          ← NEW in v2
    │       ├── TopicRankingService.java
    │       ├── StoryMergeService.java
    │       ├── ScriptGeneratorService.java  ← UPGRADED in v2
    │       ├── ScriptValidationService.java
    │       ├── SceneAssemblyService.java
    │       ├── EdgeTtsService.java
    │       ├── UnsplashService.java
    │       ├── VideoAssemblyService.java
    │       ├── VideoValidationService.java
    │       └── PortalUploadService.java
    └── resources/
        └── application.yml
```

---

## 15. Final Summary

This project is a complete AI automation system:

**NewsAPI → Groq AI Script → Edge-TTS Voice → Unsplash Image → FFmpeg Video → MP4**

Ideal for YouTube automation channels, AI content startups, social media automation tools, and SaaS video platforms.

# 🎬 Trending Video Generator

## Complete Developer Guide

**Spring Boot 3 · Java 17 · Ollama · Edge-TTS · Unsplash · FFmpeg**
**Version 1.0 | March 2026**

---

## 🚀 1. Project Overview

Trending Video Generator is a fully automated **AI-powered video generation system** that:

* Reads RSS news feeds
* Extracts trending topics
* Generates scripts using a **local LLM (Ollama)**
* Converts text to voice (Edge-TTS)
* Fetches background images (Unsplash)
* Produces **vertical short-form videos (MP4)**

👉 Ready for:

* YouTube Shorts
* Instagram Reels
* TikTok

---

### 🔄 Pipeline Summary

```bash
RSS Feed → Article Extraction → Topic Ranking → AI Script (Ollama) → 
TTS Audio (Edge-TTS) → Background Image (Unsplash) → 
Ken Burns Video (FFmpeg) → Final MP4
```

---

### ⚙️ Execution Modes

* ⏱ Runs automatically every **6 hours (scheduler)**
* 🔘 Can be triggered manually via REST API
* 💾 Stores metadata in **H2 database**

---

## 🛠️ 2. Software & Tools Required

| Tool          | Cost      | Purpose             |
| ------------- | --------- | ------------------- |
| Java 17 (JDK) | Free      | Spring Boot runtime |
| Maven 3.8+    | Free      | Build tool          |
| Ollama        | Free      | Local AI execution  |
| llama3.2:1b   | Free      | AI model (~1.3GB)   |
| Python 3.8+   | Free      | Required for TTS    |
| edge-tts      | Free      | Voice generation    |
| FFmpeg        | Free      | Video processing    |
| Unsplash API  | Free tier | Image source        |

> ⚠️ Unsplash is the only external API used.

---

## ⚙️ 3. Installation Guide

### 3.1 Java 17

```bash
java -version
```

---

### 3.2 Ollama (Local AI)

```bash
ollama pull llama3.2:1b
ollama list
```

> 💡 Use `llama3` if you have 8GB+ RAM.

---

### 3.3 Python + Edge-TTS

```bash
pip install edge-tts
pip show edge-tts
```

Test:

```bash
edge-tts --text "Hello world" --write-media test.mp3
```

---

### 3.4 FFmpeg

```bash
ffmpeg -version
```

---

### 3.5 Unsplash API

1. Create account → https://unsplash.com/developers
2. Generate Access Key
3. Add to `application.yml`

---

## ⚙️ 4. Configuration (`application.yml`)

```yaml
server:
  port: 7070

spring:
  datasource:
    url: jdbc:h2:mem:trendsdb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
  h2:
    console:
      enabled: true

app:
  rss:
    feeds:
      - https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml
      - https://feeds.bbci.co.uk/news/rss.xml

  video:
    outputDir: C:/Sham/trending_video_generator/video-output
    workspaceDir: C:/Sham/trending_video_generator/video-workspace
    ffmpegPath: C:/path/to/ffmpeg.exe

  ollama:
    baseUrl: http://localhost:11434
    model: llama3.2:1b

  unsplash:
    accessKey: YOUR_ACCESS_KEY

  edgetts:
    voice: en-US-AriaNeural
    executablePath: C:/Users/shama/.../Scripts/edge-tts.exe
```

---

## 🧠 5. Architecture Overview

### 🏗️ Core Pipeline Service

* `TrendingVideoPipelineService` → orchestrates entire workflow

---

### 📥 Ingestion Layer

* `RssFeedService`
* `ArticleExtractorService`
* `TopicRankingService`
* `StoryMergeService`

---

### 🤖 AI Script Generation

* `ScriptGeneratorService`
* `ScriptValidationService`

---

### 🎬 Scene Generation

* `SceneAssemblyService`
* `EdgeTtsService`
* `UnsplashService`

---

### 🎥 Video Processing

* `VideoAssemblyService`
* `VideoValidationService`

---

### 📤 Upload & Storage

* `PortalUploadService`
* `GeneratedVideoRepository`

---

## ▶️ 6. How to Run Locally

### Step 1 — Start Ollama

```bash
ollama serve
```

---

### Step 2 — Build

```bash
.\mvnw clean package -DskipTests
```

---

### Step 3 — Run

```bash
.\mvnw spring-boot:run
```

---

### Step 4 — Trigger Pipeline

```bash
curl -X POST http://localhost:7070/api/trends-video/run
```

---

### 📁 Output Location

```
C:\Sham\trending_video_generator\video-output\
```

---

## 🔗 7. API Endpoints

| Method | Endpoint               | Description  |
| ------ | ---------------------- | ------------ |
| GET    | /api/trends-video/test | Health check |
| POST   | /api/trends-video/run  | Run pipeline |
| GET    | /h2-console            | Database UI  |
| GET    | /actuator/health       | App health   |

---

## 🔄 8. Pipeline Execution Steps

1. Fetch RSS feeds
2. Extract article content
3. Rank trending topics
4. Merge stories
5. Generate AI script
6. Validate script
7. Generate audio
8. Download images
9. Create video scenes
10. Merge video
11. Upload & store metadata

---

## ❗ 9. Common Errors & Fixes

| Error              | Fix                       |
| ------------------ | ------------------------- |
| model not found    | `ollama pull llama3.2:1b` |
| edge-tts not found | Set full path             |
| ffmpeg not found   | Verify path               |
| memory issue       | Use smaller model         |
| upload error       | Increase file size        |
| no images          | fallback used             |

---

## 🔮 10. Planned Upgrades

* 🎭 SadTalker (AI avatar)
* 🧠 Better NLP ranking
* 🐘 PostgreSQL database
* 📊 Real-time status API
* 💰 Claude / ElevenLabs upgrade
* 👥 Multi-avatar personas

---

## 💡 Architecture Note

All AI services are modular.

👉 You can easily replace:

* Ollama → Claude API
* Edge-TTS → ElevenLabs

Without changing the pipeline.

---

## 🎯 Final Summary

This project is a **complete AI automation system**:

👉 News → AI → Voice → Video → Upload

---

## 🔥 Ideal Use Cases

* YouTube automation channels
* AI content startups
* Social media automation tools
* SaaS video platforms

---

## 📄 Source Reference

Generated from your document:

---

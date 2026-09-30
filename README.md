# DevOps Voice Companion

Ask Alexa about your builds. Get answers, not log dumps.

## What it is

A voice-driven MCP (Model Context Protocol) server that reports CI/build status and summarizes failures in plain, speakable language. Built for Amazon's **Build, Ship, Shape** Developer Hackathon — Alexa+ track + AWS Builder mini-challenge.

> "Alexa, why did the build fail?"
> → checks your GitHub Actions runs → Amazon Bedrock summarizes the failure → Alexa+ speaks the answer.

## How it works

```
Voice (Alexa+) → MCP tools (Spring Boot) → GitHub Actions API (build status, logs)
                                           → Amazon Bedrock (failure summaries)
```

## Stack

- Java 21, Spring Boot, Spring AI (MCP server over Streamable HTTP)
- Amazon Bedrock (failure summarization)
- GitHub Actions API (build status and logs)
- Docker, Railway (hosting)

## Roadmap

- [x] Repo + plan
- [ ] Phase 0: Scaffold, hello-world MCP tool, Railway deploy
- [ ] Phase 1: GitHub Actions tools (build status, failed runs, logs)
- [ ] Phase 2: Bedrock failure summarization
- [ ] Phase 3: Alexa+ integration via Alexa+ for Builders
- [ ] Phase 4: Demo video + submission

## Status

In active development. Hackathon deadline: October 23, 2026.

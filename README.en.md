<div align="center">

# 🔍 Codeprint

**Visualize a GitHub repository like an interactive circuit diagram — code structure, call flow, and DB relationships at a glance.**

A developer-facing SaaS that lets you understand a project's structure without reading the code, and catch architecture problems *before you merge*.

[![Live](https://img.shields.io/badge/Live-Demo-2563eb)](https://codeprint-iota.vercel.app)
[![Claude Code Skill](https://img.shields.io/badge/Claude%20Code-Skill-d97757)](https://github.com/PYB0514/codeprint-plugins)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6db33f)
![React](https://img.shields.io/badge/React-18-61dafb)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178c6)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-336791)
![Architecture](https://img.shields.io/badge/Architecture-DDD-8b5cf6)

</div>

<p align="center">
  <b>🇺🇸 English</b> · <a href="README.md">🇰🇷 한국어</a>
</p>

---

## 📌 Project Purpose

Most code visualization tools stop at "showing code that's already been written, after the fact." Codeprint goes a step further.

> **Core thesis — the real value of structural warnings isn't "post-hoc audit," it's "prevention before merge/push."**

Codeprint statically analyzes a GitHub repo and builds an interactive graph where **files, functions, DB tables, and APIs are nodes**, and **calls, imports, DB access, and API calls are edges**. On top of that, it automatically detects **structural/architectural problem patterns** that static analysis can catch (circular dependencies, broken interface chains, DDD boundary violations, DB layer bypass, etc.), and **posts warning comments plus a commit status on GitHub PRs so violations can be blocked before merge.**

- **Web service** — GitHub PR integration: opening a PR analyzes the changed branch and auto-comments structural warnings.
- **(planned) Desktop app** — local folder analysis: detect warnings locally before push, without sending code anywhere.

🔗 **Live:** [codeprint-iota.vercel.app](https://codeprint-iota.vercel.app) · 🧩 **Claude Code Skill:** [codeprint-plugins](https://github.com/PYB0514/codeprint-plugins) — a free Claude Code skill (Java/Kotlin/TypeScript/TSX) that wraps this project's analysis engine as a local CLI

---

## 🎯 What This Project Demonstrates

A solo-developed project that doubles as a job-search portfolio, aiming beyond simple feature delivery toward **industry-grade design and operational discipline**.

| Aspect | Details |
|---|---|
| **DDD modular monolith** | Enforces one-directional dependencies per bounded context (`Interfaces → Application → Domain ← Infrastructure`). Cross-context calls only via Port/Adapter or Facade. Structured so any domain can be split into a microservice at any time. |
| **Dogfooding** | Codeprint's warning engine is applied **to Codeprint's own code** — keeping the codebase at zero DDD boundary violations (`CROSS_DOMAIN_CALL`, `DOMAIN_IMPORTS_INFRA`, etc.). The PR review feature has actually caught violations in its own PRs and been fixed with a Port pattern. |
| **Operational discipline** | What/why written in every PR · immediate commit per feature · SemVer tagging · Flyway migration sets · no merge to main without passing CI (branch protection). |
| **Testing strategy** | TDD for domain logic with branches, state transitions, and boundary conditions; runtime verification for everything else. Repeated bugs mandate a regression test (tracked via ERROR_TRACKER). |
| **Security baseline** | Assumes "there's always a real, paying user." Ownership verification (IDOR prevention) · `@Valid` input validation · JWT HttpOnly cookies · rate limiting · sensitive data never logged. |

---

## ✨ Key Features

### Analysis engine
- **Multi-language static analysis** — a tree-sitter AST-based analyzer covering 13 languages (Java, Kotlin, TypeScript, JavaScript, Python, Go, Rust, C, C++, C#, Ruby, PHP, Swift) plus Prisma schemas. Falls back to regex when the native parser fails.
- **Framework-aware** — API endpoints (Spring, Express, NestJS, FastAPI/Flask, Django, Gin/Echo/Fiber, Rails, Laravel, ASP.NET, Ktor, Vapor) and DB entities (JPA, TypeORM, SQLAlchemy, Django ORM, Prisma, ActiveRecord, Eloquent, EF Core, Core Data, GORM, raw SQL).
- **Relationship extraction** — `IMPORT`, `FUNCTION_CALL`, `INSTANTIATION`, `CONTAINS`, `DB_READ/WRITE`, `API_CALL` edges.

### Structural warning detection (the differentiator)
Proactively detects structural/architectural issues via static analysis and classifies them by severity (HIGH/MEDIUM/LOW). 19 detectors.
- Circular dependencies (`CYCLIC_IMPORT`) · broken interface chains (`BROKEN_INTERFACE_CHAIN`) · `@Async` self-calls · DB layer bypass
- DDD boundary violations (`CROSS_DOMAIN_CALL`, `CROSS_CONTEXT_IMPORT`, `DOMAIN_IMPORTS_INFRA`) · feature-slice boundary violations (`CROSS_FEATURE_IMPORT`, `FEATURE_LAYER_VIOLATION`) · architecture-intent drift (`INTENT_DRIFT`) · dead code · high fan-out
- **False-positive calibration** — precision is continuously tuned via A/B measurement against real open-source repos (Spring PetClinic, gin, ripgrep, bulletproof-react, requests, and other per-language benchmarks). Zero-config: it automatically detects the project's structure (DDD, feature-slice) and fires only the matching rules.

### Visualization (React Flow)
- Group boxes by DDD layer / bounded context · dual view (layered ↔ domain) · saved node drag positions
- Auto flow playback (step-by-step animation along the call tree) · node search/filter/deep-link · warning node/edge highlighting
- Export graph as PNG · export AI-context tree (.md) · version retention policy (last 10 auto + 5 pinned slots)

### GitHub PR integration (product MVP)
- Analyzes the target branch for a given PR number and **auto-posts structural warnings as a PR comment**
- **Webhook automation** — auto-reviews on PR open/push after HMAC signature verification
- **Merge gate** — posts the verdict as a `codeprint/structure` commit status. Once registered as a required check in branch protection, a HIGH warning actually blocks the merge. (This repo itself is protected by the same gate — dogfooding.)

### Collaboration, community & AI
- Real-time collaboration (STOMP WebSocket — cursor overlay, invite codes, team chat) · graph sharing / live chat
- Community (gallery, feed, search, likes, bookmarks, follows, comments, DMs, notification center)
- AI integration (BYOK multi-provider key management · node/edge explanations · code generation · MCP context endpoint)

### Operations & billing
- GitHub OAuth2 login (JWT HttpOnly cookie + refresh token) · admin dashboard (JVM metrics, daily digest, inquiry tracking, plan audit log)
- Toss Payments team-seat billing · Web Push notifications · Sentry integration · "today's featured public repo" (landing-page auto-analysis showcase)

---

## 🛠 Tech Stack

**Backend** — Java 17 · Spring Boot 3.x · Spring Security (OAuth2) · Spring Data JPA (Hibernate) · PostgreSQL · Flyway · Gradle · `@Async` + WebSocket (STOMP) · Caffeine Cache · Toss Payments · Anthropic Claude API · AWS S3

**Frontend** — React 18 · TypeScript · Vite · React Flow · Zustand · Tailwind CSS · Axios · html-to-image

**Infra / DevOps** — Railway (backend + PostgreSQL) · Vercel (frontend) · GitHub Actions CI · branch protection rules · Sentry

---

## 🏛 Architecture

```
Interfaces (Controller, WebSocket)
      │  ▼ calls only its own context's Application + Facade
Application (Use Case, transaction boundary)
      │  ▼ delegates to domain methods
Domain (Entity, VO, Domain Service, Port)   ◄── Infrastructure (JPA, GitHub API, S3, Adapter)
      ▲ Domain never imports Infrastructure (one-directional)
```

- **Bounded contexts** — split into User, Project, Analysis, Graph, Community, Payment, AI, and more. Zero direct dependencies between contexts.
- **Dependency inversion** — when a context needs another, it declares a Port interface in its own domain layer, implemented as an Adapter in infrastructure.
- Details → [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · Analysis engine → [`docs/ANALYSIS_ENGINE.md`](docs/ANALYSIS_ENGINE.md)

---

## 🚀 Running Locally

```bash
# 1. DB (Docker)
docker compose up -d

# 2. Backend (Spring Boot, :8080)
cd backend && ./gradlew bootRun

# 3. Frontend (Vite, :3000 → proxies /api to :8080)
cd frontend && npm install && npm run dev
```

> GitHub OAuth, Toss, AWS, and other integrations require environment variables. See [`docs/PROJECT.md`](docs/PROJECT.md) for detailed environment setup.

---

## 🗺 Roadmap

- [x] Multi-language analysis engine (tree-sitter AST) · 19 structural warning detectors + severity · false-positive calibration
- [x] React Flow visualization · flow playback · dual domain/layer view · architecture-intent declarations · version retention policy
- [x] Real-time collaboration · community · AI integration (BYOK) · MCP context endpoint
- [x] GitHub PR webhook auto-review + `codeprint/structure` merge gate · payments · admin dashboard
- [ ] **Self-service PR gate UI** — per-project webhook issuance and connection status (v1.0 critical path, in progress)
- [x] Free distribution channel — **[published as a Claude Code Skill](https://github.com/PYB0514/codeprint-plugins)** (2026-07-11). The MCP JSON-RPC server (`POST /mcp/rpc`) was retired for lack of a real trigger, replaced by wrapping the local CLI as a Skill — see `decisions/DECISIONS_BACKEND.md` for details
- [ ] Desktop app — local folder analysis (detect file changes → auto re-check before push), no code ever leaves the machine

---

## 💳 Pricing

| Plan | Details |
|---|---|
| **Free** | Effectively all features free for individual use — unlimited analysis · graph visualization · structural warnings · community · AI explanations (bring your own API key) |
| **Pro · Desktop** | ₩4,900 per seat (currently one-time payment — recurring monthly billing in progress) — team seat billing (Toss Payments) + desktop license (upcoming). Individual billing is temporarily paused (to resume later) |

> Analysis, visualization, and the gate stay free as the distribution funnel; billing sits on the team/org tier and the desktop license.

---

<div align="center">

**Codeprint** · Solo full-stack development · [Live Demo](https://codeprint-iota.vercel.app)

</div>

# Epic 3 Context: 电商场景内选品、Listing 与历史

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Users already inside the ecommerce scene generate picklists and Listing kits on billed runs (reserve → settle only after usable artifacts persist), preview them in the workspace, export or copy for listing, retry/feedback, and browse ~60 days of their own history. Out-of-scope asks get a short friendly redirect back to picklist/Listing without charging generation credits. This epic is the product’s first paid vertical depth; category-template picker is out of scope—use one domestic default style.

## Stories

- Story 3.1: GenerationRun、SSE 与会话必绑场景
- Story 3.2: 电商场景能力包按 sceneCode 加载
- Story 3.3: 会话态工作台（侧栏 + 对话 + Computer）
- Story 3.4: 生成选品清单并结算 1 积分
- Story 3.5: 超范围短聊后友好拉回
- Story 3.6: 生成 Listing 套装并结算 1 积分
- Story 3.7: 下载图片与复制文案
- Story 3.8: 重试、「质量差」反馈与近 60 天历史

## Requirements & Constraints

- Picklist success: ~8–12 candidates each with a sellable reason; costs **1** credit; result enters history.
- Listing success: main-image plan + detail copy + display notes; costs **1** credit; download image / copy text for shop upload.
- Insufficient balance: cannot start; human-readable nudge toward plan/upgrade.
- Failure/cancel: no charge or release hold; never settle solely because the SSE stream ended.
- Retry = new billed run (charge again); “质量差” feedback saves briefly and does **not** charge generation credits.
- History: user’s last ~60 days of picklists and Listings; default all scenes for that user, filterable by scene.
- Out-of-scope chat: allow brief friendly reply, then pull back to picklist/Listing with clear capability entry points; no fake delivery; no generation debit; bound chat rounds/strategy to avoid unbounded model cost.
- Near-term: **no** category template selector—single general default style for both paths.
- Quality bar: reasons readable / not obvious junk; Listing copy editable enough to list; surface that AI output needs human review before listing.
- Each GenerationRun must support model-cost observability (fields/logs).
- Failures and errors: human-readable reasons (balance, busy, incomplete input, etc.); SSE failures via explicit fail events, not silent disconnect.

## Technical Decisions

- Billing generation streams over **SSE**; browser uses **fetch + ReadableStream + JWT**, not native `EventSource`. Closed event names only (`run_started`, `agent_started`, `message_delta`, `tool_*`, `agent_ended`, `human_input_required`, `artifact_ready`, `run_failed`, `run_settled`, …). Waiting on human input does not settle credits.
- Only **CreditLedger** mutates balances: check → reserve 1 → settle only after usable artifact is projected and persisted; else release. Agent tools must not write the ledger.
- Creating a billed session / **GenerationRun** requires `sceneId` or `sceneCode`; credits remain site-wide (not per-scene ledgers).
- Model calls only via backend `pi-ai`; business entry via **AgentSession**. Prompt/skill/tool bodies live in repo code packs keyed by `sceneCode`; never accept or return system prompts/tool defs from the browser. Ecommerce pack must cover both picklist and Listing paths.
- Artifacts: picklist (`artifact_type=picklist`) and Listing/sku (`artifact_type=sku`) via ArtifactStore; Listing images via MediaStore/OSS with **`mediaObjectId`** as canonical image identity (no second URL source of truth; no large image BLOBs in MySQL).
- Each billed attempt (including retry) = new GenerationRun + new hold; may reuse the chat session; successful artifacts stay as independent history rows (retry does not silently overwrite).
- REST/SSE (except public auth/landing): JWT required to start billed generation.

## UX & Interaction Patterns

- Session workspace: sidebar history + main chat + (after results) right **Adam's Computer** split for picklist/Listing preview—not chat-only bubbles.
- Capsules (or equivalent) trigger picklist / listing flows; empty-state shell already from Epic 2; breadcrumb stays「场景 / 电商开店」.
- Out-of-scope: friendly pull-back bubble plus capability entry buttons.
- History page: ~60-day list; same full-bleed top-bar contract (left scene/history/pricing; right credits/upgrade/avatar).
- Narrow viewports: collapsible sidebar; Computer must not kill chat readability.
- User-visible copy: no internal jargon; pull-back tone is warm, not error-code cold.

## Cross-Story Dependencies

- **Epic 1:** CreditLedger reserve/settle/month quotas and insufficient-balance messaging must already work; this epic only triggers billing on generation.
- **Epic 2:** Ecommerce workspace empty state, scene catalog/`sceneCode`, and top-bar/breadcrumb contract; Epic 3 binds billed runs to that scene and fills session + Computer.
- **Within epic:** 3.1–3.2 (run/SSE + scene pack) underpin 3.4–3.6; 3.3 consumes stream + artifacts for Computer; 3.7 needs successful Listing media/copy; 3.8 retry/history build on GenerationRun + ArtifactStore; 3.5 uses the ecommerce pack without generation debit.
- **Deferred elsewhere:** backend hard-reject for non-`AVAILABLE` scenes; multi-category templates; real payments.

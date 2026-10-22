# Workspace Agent Guidelines & Architecture Docs

This directory contains workspace customizations, rules, skills, and design documentation for AI agents working on `dart-intellij`.

## Rules

- [`websocket-design.md`](rules/websocket-design.md): Guidelines and intentions for WebSocket.

## Design Documents

- [`websocket-design.md`](design-docs/websocket-design.md): Full architectural design document for WebSocket.

## Tool Selection & Efficiency

To ensure all agentic tasks in this repository are executed as accurately, securely, and token-efficiently as possible, you must strictly follow these rules:

* **Prefer Domain-Specific MCP Tools:** Before running generic terminal commands (e.g. `grep`, `find`, or compiler check scripts), always check if a domain-specific MCP tool (such as `lsp`, `analyze_files`, or `rip_grep_packages` from `dart-mcp-server`) is available and use it instead.
* **Limit Command Output:** Any command-line tool execution (`run_command`) that might print large datasets, build logs, or file listings must have its stdout restricted using filters or limits (e.g. `head -n 50`, `git log -n 5`) to prevent context bloat.
* **Targeted File Reading:** When reading source files, specify precise line boundaries with `StartLine` and `EndLine` parameters in `view_file` to read only the code you need. Avoid loading entire files over 100 lines.

## Task Delegation & Subagents

* **Isolate Exploratory Research:** For open-ended research, codebase discovery, or scanning third-party docs, delegate the subtask to the `research` subagent. The subagent will run in a separate workspace context and return a concise summary, keeping the main conversation's history clean and token-efficient.

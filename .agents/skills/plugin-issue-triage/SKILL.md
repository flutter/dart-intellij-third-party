---
name: plugin-issue-triage
description: Automates the triage of GitHub issues in the Dart and Flutter IntelliJ plugins. Helps the engineer by fetching untriaged issues, generating AI-suggested priorities, assignees, and responses, launching a local review dashboard, and bulk-applying approved decisions to GitHub.
---

# Dart & Flutter IntelliJ Plugin Issue Triage Skill

This skill guides the process of fetching, analyzing, reviewing, and applying triage decisions to GitHub issues in the `flutter/dart-intellij-third-party` and `flutter/flutter-intellij` repositories using a local interactive dashboard.

> [!IMPORTANT]
> **WRITING GUIDELINES**
> When drafting replies, explanations, or any prose, refer to the [natural-writing](../natural-writing/SKILL.md) skill to ensure clarity, accuracy, and tone.

---

## Workflow

When the user invokes this skill, **first ask them if they want to triage the dart plugin (`flutter/dart-intellij-third-party`), the flutter plugin (`flutter/flutter-intellij`), or both.** If they select both, perform the following steps sequentially for each repository.

### Step 1: Fetch Untriaged Issues

Use the fetch script to retrieve all open issues lacking a priority label in the selected repository.

1. Run the fetch script to download the issues to a raw JSON file in your conversation-specific scratch directory:
   `python3 .agents/skills/plugin-issue-triage/scripts/fetch_issues.py --repo "<REPO_NAME>" --output-file "<appDataDir>/brain/<conversation-id>/scratch/raw_issues_<REPO_NAME_CLEANED>.json"`
   (Replace `<REPO_NAME>` with `flutter/dart-intellij-third-party` or `flutter/flutter-intellij`, and `<REPO_NAME_CLEANED>` with a filesystem-safe version like `dart` or `flutter`).

---

### Step 2: Analyze and Suggest Triage

Process the raw issues and generate recommended triage fields based on the project's triage criteria.

1. Read the triage criteria reference document: [triage_criteria.md](references/triage_criteria.md), [priorities.md](references/priorities.md), and [labels.md](references/labels.md).
2. **Natively Orchestrate Subagents**: 
   - Load the first N issues (defaulting to 10, or as requested) from the downloaded JSON.
   - Call the `invoke_subagent` tool in parallel for those issues. Prompt each subagent to analyze its assigned issue against the guidelines in [triage_criteria.md](references/triage_criteria.md), [proposed_actions.md](references/proposed_actions.md), [priorities.md](references/priorities.md), and [labels.md](references/labels.md). Instruct them to return a structured JSON block containing `priority`, `proposed_actions` (an array of tag strings from proposed_actions.md), `labels`, `reply`, and `search_keywords` (a string of 3-5 highly specific keywords or stack trace snippets designed to find duplicate issues).
   - While the subagents are running, or after they report back, use the `gh issue list --search "<search_keywords>" --state all --json number,title,state,createdAt,url --limit 3` command for each issue's generated keywords.
   - Compile their recommendations and your search results into the standard schema:
     - Ensure `assignee` is left empty by default unless there is a strong reason to assign an owner.
     - Inject `possible_duplicates` (the raw JSON array of the top 3 GitHub search results, omitting the current issue itself).
     - Inject `total_issues_count` (preserving the total count from the raw issues JSON).
     - Save the final compiled payload to `issues_to_triage_<REPO_NAME_CLEANED>.json` in the scratch directory.

---

### Step 3: Launch Review Dashboard

Launch the interactive web dashboard to allow the engineer to review and refine the suggested triages.

1. Start the local server as a background task, pointing it to your scratch directory:
   `python3 .agents/skills/plugin-issue-triage/scripts/launch_dashboard.py --data-file "<appDataDir>/brain/<conversation-id>/scratch/issues_to_triage_<REPO_NAME_CLEANED>.json" --output-file "<appDataDir>/brain/<conversation-id>/scratch/triage_decisions_<REPO_NAME_CLEANED>.json"`
   Set `WaitMsBeforeAsync` to `1000` so the server runs in the background.
2. **Wait for Completion**: Stop calling tools and go idle. The launcher will automatically open the browser for the user and block until they click "Apply Triages" or "Abort". Once the user acts, the background task will complete, and you will receive a notification with the exit status.
3. **Verify Exit Status**:
   - If the task exited with status `0` (approved), proceed to apply the decisions.
   - If the task exited with a non-zero status (abort), don't modify any issues. You MUST STOP and ask the user for further instructions.

---

### Step 4: Apply Decisions to GitHub

Once the dashboard task exits successfully, **Execute Approved Decisions**: Run the apply script to update the approved labels, assignees, and comments on GitHub:

`python3 .agents/skills/plugin-issue-triage/scripts/apply_triage.py --decisions-file "<appDataDir>/brain/<conversation-id>/scratch/triage_decisions_<REPO_NAME_CLEANED>.json"`

---

## Bundled Resources
- **[triage_criteria.md](references/triage_criteria.md)**: Guide for classifying issues and assigning priorities.
- **[priorities.md](references/priorities.md)**: Priority mappings and guidelines.
- **[labels.md](references/labels.md)**: Label heuristics and guidelines.
- **`scripts/fetch_issues.py`**: Script to query open, untriaged issues via the GitHub CLI.
- **`scripts/launch_dashboard.py`**: Local HTTP server that opens the interactive web dashboard in the user's browser.
- **`assets/triage_dashboard.html`**: HTML/CSS/JS template for the triage review interface.
- **`scripts/apply_triage.py`**: Script to apply finalized triage decisions to GitHub.

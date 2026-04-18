---
name: starter
description: Practical execution skill for day-to-day coding tasks. Use when the user asks for implementation, quick repo checks, status summaries, or commit message drafting with a consistent output format.
---

# Starter Skill

Use this skill as a lightweight default workflow for common coding tasks.

## Workflow (Lite)

1. Clarify the exact task goal in one sentence.
2. Run quick Git checks before editing.
3. Implement with the smallest safe change set.
4. Summarize using the output template below.
5. If asked to commit, draft a commit message in the required format.

## Git checks before editing

Run these checks first:

```bash
git status --short
git diff -- . ':(exclude)package-lock.json' ':(exclude)pnpm-lock.yaml'
git log --oneline -5
```

Notes:
- Never revert unrelated local changes.
- Never run destructive commands without explicit user approval.

## Output template

Use this structure in responses:

```markdown
Task understanding:
- <one-line understanding>

Actions taken:
- <key action 1>
- <key action 2>

Result:
- <what changed or what was verified>

Next step (optional):
- <single recommended follow-up>
```

## Commit message format

When user asks to commit, use this style:

```text
<type>(<scope>): <short summary>

<why this change is needed>
```

Recommended `type` values:
- `feat`: new behavior
- `fix`: bug fix
- `refactor`: code cleanup without behavior change
- `docs`: documentation only
- `test`: tests only

## Validation prompt

Ask the assistant:

`Use my-plugin starter skill to summarize repo status and propose a commit message.`

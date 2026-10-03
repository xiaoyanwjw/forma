The messages above are a conversation (and optionally an existing summary) to turn into a structured context checkpoint that another LLM will use to continue the work.

## When there is NO `<previous-summary>` above

Create a new structured summary from the conversation only.

## When there IS a `<previous-summary>` above

Update that summary with the NEW conversation messages:
- PRESERVE all existing information from the previous summary
- ADD new progress, decisions, and context from the new messages
- UPDATE the Progress section: move items from "In Progress" to "Done" when completed
- UPDATE "Next Steps" based on what was accomplished
- If something is no longer relevant, you may remove it
- Do NOT drop prior goals, constraints, or key decisions unless the new messages clearly supersede them

## Output format (both cases)

Use this EXACT format:

## Goal
[What is the user trying to accomplish? Can be multiple items if the session covers different tasks.]
[If updating: preserve existing goals, add new ones if the task expanded]

## Constraints & Preferences
- [Any constraints, preferences, or requirements mentioned by user]
- [Or "(none)" if none were mentioned]
[If updating: preserve existing, add new ones discovered]

## Progress
### Done
- [x] [Completed tasks/changes]
[If updating: include previously done items AND newly completed items]

### In Progress
- [ ] [Current work]
[If updating: refresh based on latest progress]

### Blocked
- [Issues preventing progress, if any]
[If updating: current blockers — remove if resolved]

## Key Decisions
- **[Decision]**: [Brief rationale]
[If updating: preserve all previous, add new]

## Next Steps
1. [Ordered list of what should happen next]
[If updating: refresh based on current state]

## Critical Context
- [Any data, examples, or references needed to continue]
- [Or "(none)" if not applicable]
[If updating: preserve important context, add new if needed]

Keep each section concise. Preserve exact file paths, function names, and error messages.
Match the primary language of the conversation (and of any previous summary body) for all summary content (not the English section headings).

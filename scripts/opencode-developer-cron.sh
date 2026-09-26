#!/bin/sh
set -u

PROJECT_DIR="/Users/davidpe/dev/projects/cosmic-aces"
OPENCODE_BIN="/Users/davidpe/.opencode/bin/opencode"
PROMPT_FILE="$PROJECT_DIR/docs/agent-workflow/opencode-developer.md"
LOCK_FILE="/private/tmp/cosmic-aces-opencode-developer-$(/usr/bin/id -u).lock"
LOG_DIRECTORY="$HOME/Library/Logs/CosmicAces"
LOG_FILE="$LOG_DIRECTORY/opencode-developer-$(/bin/date +%Y-%m-%d).log"
PATH="/Users/davidpe/.opencode/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"
export PATH

/bin/mkdir -p "$LOG_DIRECTORY"
exec >> "$LOG_FILE" 2>&1

printf '\n[%s] Starting Cosmic Aces OpenCode Developer run.\n' "$(/bin/date '+%Y-%m-%d %H:%M:%S %Z')"

if [ ! -x "$OPENCODE_BIN" ]; then
  printf '[%s] ERROR: OpenCode executable is unavailable: %s\n' "$(/bin/date '+%Y-%m-%d %H:%M:%S %Z')" "$OPENCODE_BIN"
  exit 127
fi

if [ ! -r "$PROMPT_FILE" ]; then
  printf '[%s] ERROR: Developer prompt is unavailable: %s\n' "$(/bin/date '+%Y-%m-%d %H:%M:%S %Z')" "$PROMPT_FILE"
  exit 1
fi

PROMPT=$(/usr/bin/awk '
  /^```text[[:space:]]*$/ && !seen { capture = 1; seen = 1; next }
  capture && /^```[[:space:]]*$/ { exit }
  capture { print }
' "$PROMPT_FILE")

if [ -z "$PROMPT" ]; then
  printf '[%s] ERROR: No fenced text prompt found in %s\n' "$(/bin/date '+%Y-%m-%d %H:%M:%S %Z')" "$PROMPT_FILE"
  exit 1
fi

cd "$PROJECT_DIR" || exit 1

/usr/bin/lockf -t 0 "$LOCK_FILE" "$OPENCODE_BIN" run \
  --agent developer \
  --model opencode-go/deepseek-v4-flash \
  "$PROMPT"
RESULT=$?

printf '[%s] OpenCode Developer run finished with exit code %s.\n' "$(/bin/date '+%Y-%m-%d %H:%M:%S %Z')" "$RESULT"
exit "$RESULT"

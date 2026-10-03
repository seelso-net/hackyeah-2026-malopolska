#!/usr/bin/env bash
# Maria's case, end to end, through the REST API: a resident report joins an existing case, the moderator
# approves the AI's match, a doer accepts and resolves it, Maria confirms it helped, and the AI's draft of
# the next playbook version is published. Needs curl and jq, and a freshly started app (DEMO_RESET=true).
#
#   ./scripts/demo-flow.sh                 # against http://localhost:8080
#   BASE=https://my-app.fly.dev ./scripts/demo-flow.sh
#   PAUSE=1 ./scripts/demo-flow.sh         # wait for Enter between steps (for presenting)
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
PAUSE="${PAUSE:-0}"
CASE_412="50000000-0000-4000-8000-000000000001"
TMP="$(mktemp -d)"
trap 'kill $(jobs -p) 2>/dev/null || true; rm -rf "$TMP"' EXIT

command -v jq >/dev/null || { echo "Please install jq (https://jqlang.github.io/jq/)"; exit 1; }

bold() { printf '\n\033[1m%s\033[0m\n' "$*"; }
say() { printf '  %s\n' "$*"; }
step() {
  if [ "$PAUSE" = "1" ]; then read -r -p $'\n(press Enter) ' _; fi
  bold "$*"
}
# api <user> <METHOD> <path> [json]
api() {
  local user="$1" method="$2" path="$3" body="${4:-}"
  local args=(-sS -X "$method" -H "X-Demo-User: $user" -H "Accept: application/json")
  if [ -n "$body" ]; then args+=(-H "Content-Type: application/json" -d "$body"); fi
  local out status
  out="$(curl "${args[@]}" -w $'\n%{http_code}' "$BASE$path")"
  status="${out##*$'\n'}"
  out="${out%$'\n'*}"
  if [ "${status:0:1}" != "2" ]; then
    echo "  ! $method $path as $user -> HTTP $status: $out" >&2
    exit 1
  fi
  printf '%s' "$out"
}

bold "0. Waiting for the app at $BASE (startup vectors and AI suggestions)"
for _ in $(seq 1 90); do
  if curl -sf "$BASE/q/health/started" >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -sf "$BASE/q/health/started" >/dev/null || { echo "The app is not ready at $BASE"; exit 1; }
engine="$(curl -s "$BASE/q/health/started" | jq -r '.checks[] | select(.name=="ai-startup") | .data.engine')"
say "Ready. AI engine: $engine"
status="$(api ewa GET "/api/moderation/cases/$CASE_412" | jq -r .status)"
if [ "$status" != "SUGGESTED" ]; then
  echo "Case R-0412 is $status, not SUGGESTED: restart the app with DEMO_RESET=true to replay the demo."
  exit 1
fi

# Live updates, as the phones and the moderator's browser would get them
for who in maria ewa kasia; do
  curl -sN "$BASE/api/stream?as=$who" > "$TMP/$who.sse" 2>/dev/null &
done
sleep 1

step "1. One platform, any community: the same lifecycle under each community's own names"
for slug in riverside oak-street-coop; do
  api piotr GET "/api/communities/$slug?lang=en" \
    | jq -r '"  \(.name): " + ([.statuses[] | select(.visible) | .label] | join(" -> "))'
done

step "2. Maria reports in Polish from her phone (typed or dictated)"
TEXT="Moja sąsiadka z czwartego piętra ma 84 lata i od miesiąca nie wychodzi z domu, bo w bloku nie ma windy. Ktoś musiałby jej pomagać z zakupami."
say "\"$TEXT\""
REPORT="$(api maria POST /api/reports "$(jq -n --arg t "$TEXT" \
  '{community: "riverside", kind: "NEED", text: $t, lat: 52.2499, lng: 21.0415, address: "Wiązowa 4"}')" | jq -r .reportId)"
say "202 Accepted: report $REPORT; the AI reads it in the background"

step "3. What the AI understood, and the similar case it found nearby"
for _ in $(seq 1 60); do
  r="$(api maria GET "/api/reports/$REPORT")"
  [ "$(jq -r .status <<<"$r")" = "PROCESSED" ] && break
  sleep 1
done
jq -r '"  category: \(.ai.categoryLabel) | urgency: \(.ai.urgency) | title: \(.ai.title.text)",
       (if .similarCase then "  similar: \(.similarCase.number) \(.similarCase.title.text) (\(.similarCase.reportCount) reports, \(.similarCase.distanceMeters) m away, similarity \(.similarCase.similarity))" else "  no similar case nearby" end)' <<<"$r"
SIMILAR="$(jq -r '.similarCase.id // empty' <<<"$r")"
[ "$SIMILAR" = "$CASE_412" ] || { echo "  Expected the AI to find R-0412; tune app.matching.*.min-case-similarity"; exit 1; }

step "4. Maria taps \"Me too\": her report joins R-0412 instead of opening a duplicate"
api maria POST "/api/reports/$REPORT/submit" "{\"caseId\": \"$SIMILAR\"}" | jq -r '"  \(.result) -> case \(.caseId)"'

step "5. Ewa's moderator queue (English): every case already has the AI's proposal"
api ewa GET "/api/moderation/queue?community=riverside" \
  | jq -r '.items[] | "  \(.number) [\(.urgency)] \(.title.text) (reports \(.reportCount), supporters \(.supporterCount)) - \(.aiStatus): \(.playbookTitle.text // "no playbook yet")"'

step "6. The proposal for R-0412: a playbook proven in another district, and who could do it"
M="$(api ewa GET "/api/moderation/cases/$CASE_412")"
jq -r '.suggestion | "  playbook: \(.playbook.title.text) v\(.playbook.version) (from \(.playbook.origin))",
       "  why: \(.reason.text)",
       (.doers[] | "  \(if .selected then "[x]" else "[ ]" end) \(.name) (\(.kind)): \(.reason.text)")' <<<"$M"
SUGGESTION="$(jq -r .suggestion.id <<<"$M")"

step "7. Ewa approves the AI's picks: assignments go out and the playbook becomes a checklist"
api ewa POST "/api/moderation/suggestions/$SUGGESTION/approve" '{}' \
  | jq -r '"  status: \(.statusLabel)", (.helpers[] | "  offered to \(.name) (\(.kind))"), (.tasks[] | "  task \(.position). \(.title.text) -> \(.owner // "anyone")")'

step "8. Kasia (Good Neighbours Foundation) opens her inbox and accepts"
INBOX="$(api kasia GET /api/doer/assignments)"
ASSIGNMENT="$(jq -r --arg c "$CASE_412" '[.[] | select(.case.id == $c)][0].id' <<<"$INBOX")"
jq -r --arg c "$CASE_412" '.[] | select(.case.id == $c) | "  \(.status): \(.case.number) \(.case.title.text) at \(.case.areaLabel), \(.tasks | length) steps from \"\(.playbook.title.text)\""' <<<"$INBOX"
api kasia POST "/api/assignments/$ASSIGNMENT/accept" | jq -r '"  -> \(.status); case is now \(.case.statusLabel)"'

step "9. Kasia works through her steps and reports back with what she learned"
for task in $(api kasia GET /api/doer/assignments | jq -r --arg a "$ASSIGNMENT" '.[] | select(.id == $a) | .tasks[] | select(.assignmentId == $a) | .id'); do
  api kasia PATCH "/api/tasks/$task" '{"done": true}' | jq -r '"  [x] \(.title.text)"'
done
UPDATE="Wywieście listę chętnych na drzwiach każdej klatki. Zgłosiło się 9 sąsiadów, więcej niż z ulotek. Wszyscy seniorzy z Wiązowej 4 i 6 mają już pomocnika do zakupów i wizyt u lekarza."
say "update: \"$UPDATE\""
api kasia POST "/api/cases/$CASE_412/updates" "$(jq -n --arg t "$UPDATE" '{text: $t, resolve: true}')" \
  | jq -r '"  case is now \(.statusLabel)"'

step "10. Maria's phone asks \"Did this help?\" She answers yes"
api maria GET "/api/cases/$CASE_412" | jq -r '"  \(.number) \(.title.text): \(.statusLabel); last update: \(.timeline | map(select(.type == "UPDATE_POSTED")) | last | .text.text)"'
api maria POST "/api/cases/$CASE_412/outcome" '{"outcome": "HELPED"}' | jq -r '"  -> \(.statusLabel) (\(.outcomes.helped) helped)"'

step "11. The case closes and the AI drafts Stair Buddies v2 from what actually happened"
for _ in $(seq 1 60); do
  P="$(api ewa GET /api/playbooks/stair-buddies)"
  [ "$(jq -r '.draft != null' <<<"$P")" = "true" ] && break
  sleep 1
done
jq -r '"  draft v\(.draft.number) by \(.draft.draftedBy): \(.draft.changeNotes.text)",
       (.draft.steps[] | "  \(.position). \(.title.text)\(if .change then "  <- \(.change)" else "" end)"),
       "  Ewa can publish: \(.me.canPublish)"' <<<"$P"

step "12. Ewa publishes v2: the next district to report this problem starts from a better recipe"
api ewa POST "/api/playbooks/stair-buddies/versions/$(jq -r .draft.number <<<"$P")/publish" \
  | jq -r '"  current version: v\(.current.number), used in \(.results.cases) cases in \(.results.communities | join(" and ")), \(.results.residentsHelped) residents said it helped"'

step "13. What the live streams delivered meanwhile"
sleep 1
for who in maria ewa kasia; do
  events="$(grep -o '"type":"[A-Z_]*"' "$TMP/$who.sse" | cut -d'"' -f4 | grep -v -e HEARTBEAT -e CONNECTED | sort | uniq -c | awk '{printf "%s x%s, ", $2, $1}')"
  say "$who: ${events%, }"
done
bold "Done. Open $BASE/q/swagger-ui to try any step by hand."

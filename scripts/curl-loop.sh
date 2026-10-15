#!/usr/bin/env bash
# Demo helper: hits an app URL 5x per second and prints status code + pod + version.
# Run it during a rolling update to show there are no failed requests.
# Usage: scripts/curl-loop.sh http://hello.127.0.0.1.nip.io
URL="${1:?usage: curl-loop.sh <app url>}"
while true; do
  out=$(curl -s -m 2 -w ' %{http_code}' "$URL" || echo " ERR")
  code="${out##* }"
  body="${out% *}"
  pod=$(echo "$body" | sed -n 's/.*"pod":"\([^"]*\)".*/\1/p')
  ver=$(echo "$body" | sed -n 's/.*"version":"\([^"]*\)".*/\1/p')
  printf '%s  %s  %-40s %s\n' "$(date +%T)" "$code" "$pod" "$ver"
  sleep 0.2
done

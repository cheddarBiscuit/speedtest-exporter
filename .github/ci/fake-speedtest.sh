#!/bin/sh
# Stands in for the Ookla CLI so CI can scrape /metrics without using bandwidth.
if [ "$1" = "--version" ]; then
  echo "Speedtest by Ookla"
  exit 0
fi
cat <<'RESULT'
{"type":"result","server":{"id":1234},"ping":{"jitter":0.5,"latency":12.3},
 "download":{"bandwidth":12500000},"upload":{"bandwidth":2500000}}
RESULT

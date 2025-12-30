#!/usr/bin/env bash
set -e

cleanup() {
  podman compose -f compose.dev.yml down
}

trap cleanup EXIT INT TERM

podman compose -f compose.dev.yml up -d
podman compose -f compose.dev.yml logs -f

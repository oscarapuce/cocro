#!/usr/bin/env bash
set -e

cleanup() {
  podman compose -f infra/compose.dev.yml down
}

trap cleanup EXIT INT TERM

podman compose -f infra/compose.dev.yml up -d
podman compose -f infra/compose.dev.yml logs -f

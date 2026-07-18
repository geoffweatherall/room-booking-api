#!/usr/bin/env bash
# Builds and runs the /verify acceptance tests (mvn verify) against the deployed mootmaker API.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ -z "${1:-}" ]]; then
  echo "Usage: ./verify.sh <environment>   (e.g. test, production, or your own name)" >&2
  exit 1
fi

source ./authenticate.sh "$1"

mvn -f verify/pom.xml clean verify

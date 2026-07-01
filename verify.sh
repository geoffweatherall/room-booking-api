#!/usr/bin/env bash
# Builds and runs the /verify acceptance tests (mvn verify) against the deployed room-booking API.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

source ./api-authentic.sh

mvn -f verify/pom.xml clean verify

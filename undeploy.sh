#!/usr/bin/env bash
# Destroys all AWS resources created by deploy.sh: the AppSync API, all Lambda functions, and
# the DynamoDB tables (including any stored rooms/people/bookings data).
#
# NOTE: this is DESTRUCTIVE and IRREVERSIBLE. Terraform will prompt for interactive confirmation
# before deleting anything; this script intentionally does not pass -auto-approve.
set -euo pipefail
cd "$(dirname "$0")"

terraform -chdir=deploy/terraform destroy

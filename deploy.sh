#!/usr/bin/env bash
# Builds the Lambda jar and deploys the room-booking API to AWS via Terraform,
# into the given environment (e.g. "test", "production", or a developer's own
# name for a personal sandbox - see the room-booking project README for the
# full multi-environment how-to).
# NOTE: `terraform apply -auto-approve` creates real AWS resources in whatever
# account/credentials are active. Run this deliberately, not from automation.
set -euo pipefail
cd "$(dirname "$0")"

environment="${1:-}"
if [[ -z "${environment}" ]]; then
  echo "Usage: ./deploy.sh <environment>   (e.g. test, production, or your own name)" >&2
  exit 1
fi
if [[ ! "${environment}" =~ ^[a-z0-9-]+$ ]]; then
  echo "environment must contain only lowercase letters, digits, and hyphens: '${environment}'" >&2
  exit 1
fi

# Isolates this environment's Terraform provider cache/backend pointer from
# other environments, so deploying "test" and "production" from the same
# checkout (even concurrently) can't cross-contaminate each other.
export TF_DATA_DIR=".terraform-${environment}"

mvn -f impl/pom.xml clean package

terraform -chdir=deploy/terraform init -backend-config=backend.hcl -backend-config="key=${environment}/room-booking-api/terraform.tfstate"
terraform -chdir=deploy/terraform apply -auto-approve -var="environment=${environment}"

#!/usr/bin/env bash
# Builds the Lambda jar and deploys the room-booking API to AWS via Terraform.
# NOTE: `terraform apply -auto-approve` creates real AWS resources in whatever
# account/credentials are active. Run this deliberately, not from automation.
set -euo pipefail
cd "$(dirname "$0")/.."

mvn -f impl/pom.xml clean package

terraform -chdir=deploy/terraform init
terraform -chdir=deploy/terraform apply -auto-approve

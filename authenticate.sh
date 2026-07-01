#!/usr/bin/env bash
# Exports GRAPHQL_API_URL and GRAPHQL_API_KEY (read from Terraform outputs) into
# the current shell, for use with the /verify acceptance tests or api/requests.http.
#
# Must be SOURCED, not executed, so the exports persist in your shell:
#   source authenticate.sh
#   . ./authenticate.sh

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  echo "authenticate.sh must be sourced, not executed, so the exports persist in your shell." >&2
  echo "Run: source authenticate.sh" >&2
  exit 1
fi

_authenticate_script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_authenticate_terraform_dir="${_authenticate_script_dir}/deploy/terraform"

if ! GRAPHQL_API_URL="$(terraform -chdir="${_authenticate_terraform_dir}" output -raw graphql_api_url 2>/dev/null)"; then
  echo "Failed to read graphql_api_url from Terraform. Has the API been deployed (deploy.sh)?" >&2
  unset _authenticate_script_dir _authenticate_terraform_dir
  return 1
fi

if ! GRAPHQL_API_KEY="$(terraform -chdir="${_authenticate_terraform_dir}" output -raw graphql_api_key 2>/dev/null)"; then
  echo "Failed to read graphql_api_key from Terraform. Has the API been deployed (deploy.sh)?" >&2
  unset _authenticate_script_dir _authenticate_terraform_dir GRAPHQL_API_URL
  return 1
fi

export GRAPHQL_API_URL
export GRAPHQL_API_KEY

echo "Exported GRAPHQL_API_URL and GRAPHQL_API_KEY."

unset _authenticate_script_dir _authenticate_terraform_dir

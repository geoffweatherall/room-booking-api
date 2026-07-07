#!/usr/bin/env bash
# Exports the deployed API's endpoint and Cognito authentication settings (read
# from Terraform outputs) into the current shell, for use with the /verify
# acceptance tests, api/requests.http, and the webapp's deploy/e2e tests.
#
# Exported variables:
#   GRAPHQL_API_URL            GraphQL endpoint
#   COGNITO_USER_POOL_ID       Cognito user pool id
#   COGNITO_WEBAPP_CLIENT_ID   Public app client id for the browser webapp
#   COGNITO_TOKEN_URL          OAuth2 token endpoint (client_credentials flow)
#   COGNITO_TEST_CLIENT_ID     Acceptance-test app client id
#   COGNITO_TEST_CLIENT_SECRET Acceptance-test app client secret
#   COGNITO_TEST_SCOPE         OAuth2 scope for acceptance-test tokens
#   E2E_USER_EMAIL             Pre-confirmed user for webapp Playwright tests
#   E2E_USER_PASSWORD          Password for that user
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
_authenticate_failed=""

_authenticate_read_output() {
  local var_name="$1" output_name="$2" value
  if ! value="$(terraform -chdir="${_authenticate_terraform_dir}" output -raw "${output_name}" 2>/dev/null)"; then
    echo "Failed to read ${output_name} from Terraform. Has the API been deployed (deploy.sh)?" >&2
    _authenticate_failed="true"
    return 1
  fi
  export "${var_name}=${value}"
}

_authenticate_read_output GRAPHQL_API_URL graphql_api_url &&
  _authenticate_read_output COGNITO_USER_POOL_ID cognito_user_pool_id &&
  _authenticate_read_output COGNITO_WEBAPP_CLIENT_ID cognito_webapp_client_id &&
  _authenticate_read_output COGNITO_TOKEN_URL cognito_token_url &&
  _authenticate_read_output COGNITO_TEST_CLIENT_ID cognito_test_client_id &&
  _authenticate_read_output COGNITO_TEST_CLIENT_SECRET cognito_test_client_secret &&
  _authenticate_read_output COGNITO_TEST_SCOPE cognito_test_scope &&
  _authenticate_read_output E2E_USER_EMAIL e2e_user_email &&
  _authenticate_read_output E2E_USER_PASSWORD e2e_user_password

if [[ -z "${_authenticate_failed}" ]]; then
  echo "Exported GRAPHQL_API_URL and the COGNITO_*/E2E_* authentication variables."
fi

unset -f _authenticate_read_output
unset _authenticate_script_dir _authenticate_terraform_dir

[[ -z "${_authenticate_failed}" ]] || { unset _authenticate_failed; return 1; }
unset _authenticate_failed

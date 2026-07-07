output "graphql_api_url" {
  description = "The GraphQL endpoint URL for the room-booking API."
  value       = aws_appsync_graphql_api.this.uris["GRAPHQL"]
}

output "cognito_user_pool_id" {
  description = "Id of the Cognito user pool that authenticates API callers."
  value       = aws_cognito_user_pool.this.id
}

output "cognito_webapp_client_id" {
  description = "Public app client id used by the browser webapp to sign users in."
  value       = aws_cognito_user_pool_client.webapp.id
}

output "cognito_token_url" {
  description = "OAuth2 token endpoint used to obtain client_credentials access tokens."
  value       = "https://${aws_cognito_user_pool_domain.this.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/token"
}

output "cognito_test_client_id" {
  description = "App client id the acceptance tests use with the client_credentials flow."
  value       = aws_cognito_user_pool_client.acceptance_tests.id
}

output "cognito_test_client_secret" {
  description = "App client secret the acceptance tests use with the client_credentials flow."
  value       = aws_cognito_user_pool_client.acceptance_tests.client_secret
  sensitive   = true
}

output "cognito_test_scope" {
  description = "OAuth2 scope requested by the acceptance tests' client_credentials tokens."
  value       = "${aws_cognito_resource_server.api.identifier}/execute"
}

output "e2e_user_email" {
  description = "Email of the pre-confirmed user the webapp Playwright tests sign in as."
  value       = aws_cognito_user.e2e.username
}

output "e2e_user_password" {
  description = "Password of the pre-confirmed user the webapp Playwright tests sign in as."
  value       = random_password.e2e_user.result
  sensitive   = true
}

data "aws_caller_identity" "current" {}

resource "aws_cognito_user_pool" "this" {
  name = "${var.project_name}-users"

  # Users sign in with their email address; Cognito emails a verification code
  # on sign-up (COGNITO_DEFAULT sender, fine at demo volumes).
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }
}

# Public (no secret) client used by the room-booking-webapp browser SPA.
resource "aws_cognito_user_pool_client" "webapp" {
  name         = "${var.project_name}-webapp"
  user_pool_id = aws_cognito_user_pool.this.id

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

  prevent_user_existence_errors = "ENABLED"
}

# Hosted domain for the user pool - only needed for the OAuth2 token endpoint
# (https://<domain>.auth.<region>.amazoncognito.com/oauth2/token) that the
# acceptance tests use. The account id makes the prefix globally unique.
resource "aws_cognito_user_pool_domain" "this" {
  domain       = "${var.project_name}-${data.aws_caller_identity.current.account_id}"
  user_pool_id = aws_cognito_user_pool.this.id
}

# Resource server defining the custom scope granted to client_credentials tokens.
resource "aws_cognito_resource_server" "api" {
  identifier   = "${var.project_name}-api"
  name         = "${var.project_name}-api"
  user_pool_id = aws_cognito_user_pool.this.id

  scope {
    scope_name        = "execute"
    scope_description = "Full access to the room-booking GraphQL API"
  }
}

# Confidential (secret-holding) client for the /verify acceptance tests: the
# OAuth2 client_credentials flow exchanges the id/secret for a JWT access token
# without any human user or password being involved.
resource "aws_cognito_user_pool_client" "acceptance_tests" {
  name            = "${var.project_name}-acceptance-tests"
  user_pool_id    = aws_cognito_user_pool.this.id
  generate_secret = true

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_scopes                 = ["${aws_cognito_resource_server.api.identifier}/execute"]
  supported_identity_providers         = ["COGNITO"]
}

# Pre-confirmed user for the webapp's Playwright end-to-end tests, which sign
# in through the real UI (the browser sign-in flow inherently needs a user,
# unlike the API acceptance tests which use client_credentials above).
resource "random_password" "e2e_user" {
  length           = 20
  min_lower        = 2
  min_upper        = 2
  min_numeric      = 2
  min_special      = 2
  override_special = "!@#$%^&*()-_=+"
}

resource "aws_cognito_user" "e2e" {
  user_pool_id = aws_cognito_user_pool.this.id
  username     = "e2e-tests@example.com"
  password     = random_password.e2e_user.result

  attributes = {
    email          = "e2e-tests@example.com"
    email_verified = "true"
  }
}

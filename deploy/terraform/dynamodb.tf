resource "aws_dynamodb_table" "rooms" {
  name         = "${local.resource_prefix}-rooms"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }
}

resource "aws_dynamodb_table" "people" {
  name         = "${local.resource_prefix}-people"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }

  # Populated only for people created by the PostConfirmation sign-up trigger (guests added
  # directly have no Cognito account and so no cognitoSub). The index lets that trigger check
  # "does a Person already exist for this sub" so a retried invocation doesn't create a
  # duplicate, lets Query.myPerson resolve the signed-in caller's own Person (see
  # MyPersonHandler), and will also let a future account-deletion flow find the Person linked
  # to a given Cognito user. ALL projection (rather than KEYS_ONLY) so Query.myPerson can read
  # the full item straight off the index without a second GetItem - the table is tiny, so the
  # extra copy of each item's attributes costs effectively nothing.
  attribute {
    name = "cognitoSub"
    type = "S"
  }

  global_secondary_index {
    name            = "cognitoSub-index"
    hash_key        = "cognitoSub"
    projection_type = "ALL"
  }
}

resource "aws_dynamodb_table" "bookings" {
  name         = "${local.resource_prefix}-bookings"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }
}

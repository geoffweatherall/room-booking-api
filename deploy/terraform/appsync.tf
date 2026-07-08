resource "aws_appsync_graphql_api" "this" {
  name                = "${local.resource_prefix}-api"
  authentication_type = "AMAZON_COGNITO_USER_POOLS"
  schema              = file("${path.module}/../../api/room-booking.graphql")

  user_pool_config {
    user_pool_id   = aws_cognito_user_pool.this.id
    aws_region     = var.aws_region
    default_action = "ALLOW"
  }
}

resource "aws_appsync_datasource" "list_rooms" {
  api_id           = aws_appsync_graphql_api.this.id
  name             = "ListRoomsDataSource"
  type             = "AWS_LAMBDA"
  service_role_arn = aws_iam_role.appsync_lambda_invoke.arn

  lambda_config {
    function_arn = aws_lambda_function.list_rooms.arn
  }
}

resource "aws_appsync_datasource" "list_people" {
  api_id           = aws_appsync_graphql_api.this.id
  name             = "ListPeopleDataSource"
  type             = "AWS_LAMBDA"
  service_role_arn = aws_iam_role.appsync_lambda_invoke.arn

  lambda_config {
    function_arn = aws_lambda_function.list_people.arn
  }
}

resource "aws_appsync_datasource" "create_room" {
  api_id           = aws_appsync_graphql_api.this.id
  name             = "CreateRoomDataSource"
  type             = "AWS_LAMBDA"
  service_role_arn = aws_iam_role.appsync_lambda_invoke.arn

  lambda_config {
    function_arn = aws_lambda_function.create_room.arn
  }
}

resource "aws_appsync_datasource" "create_person" {
  api_id           = aws_appsync_graphql_api.this.id
  name             = "CreatePersonDataSource"
  type             = "AWS_LAMBDA"
  service_role_arn = aws_iam_role.appsync_lambda_invoke.arn

  lambda_config {
    function_arn = aws_lambda_function.create_person.arn
  }
}

resource "aws_appsync_datasource" "list_bookings" {
  api_id           = aws_appsync_graphql_api.this.id
  name             = "ListBookingsDataSource"
  type             = "AWS_LAMBDA"
  service_role_arn = aws_iam_role.appsync_lambda_invoke.arn

  lambda_config {
    function_arn = aws_lambda_function.list_bookings.arn
  }
}

resource "aws_appsync_datasource" "create_booking" {
  api_id           = aws_appsync_graphql_api.this.id
  name             = "CreateBookingDataSource"
  type             = "AWS_LAMBDA"
  service_role_arn = aws_iam_role.appsync_lambda_invoke.arn

  lambda_config {
    function_arn = aws_lambda_function.create_booking.arn
  }
}

resource "aws_appsync_datasource" "reset" {
  api_id           = aws_appsync_graphql_api.this.id
  name             = "ResetDataSource"
  type             = "AWS_LAMBDA"
  service_role_arn = aws_iam_role.appsync_lambda_invoke.arn

  lambda_config {
    function_arn = aws_lambda_function.reset.arn
  }
}

locals {
  direct_lambda_request_template  = "{\"version\":\"2018-05-29\",\"operation\":\"Invoke\",\"payload\":$util.toJson($ctx)}"
  direct_lambda_response_template = "$util.toJson($ctx.result)"
}

resource "aws_appsync_resolver" "rooms" {
  api_id            = aws_appsync_graphql_api.this.id
  type              = "Query"
  field             = "rooms"
  data_source       = aws_appsync_datasource.list_rooms.name
  request_template  = local.direct_lambda_request_template
  response_template = local.direct_lambda_response_template
}

resource "aws_appsync_resolver" "people" {
  api_id            = aws_appsync_graphql_api.this.id
  type              = "Query"
  field             = "people"
  data_source       = aws_appsync_datasource.list_people.name
  request_template  = local.direct_lambda_request_template
  response_template = local.direct_lambda_response_template
}

resource "aws_appsync_resolver" "create_room" {
  api_id            = aws_appsync_graphql_api.this.id
  type              = "Mutation"
  field             = "createRoom"
  data_source       = aws_appsync_datasource.create_room.name
  request_template  = local.direct_lambda_request_template
  response_template = local.direct_lambda_response_template
}

resource "aws_appsync_resolver" "create_person" {
  api_id            = aws_appsync_graphql_api.this.id
  type              = "Mutation"
  field             = "createPerson"
  data_source       = aws_appsync_datasource.create_person.name
  request_template  = local.direct_lambda_request_template
  response_template = local.direct_lambda_response_template
}

resource "aws_appsync_resolver" "bookings" {
  api_id            = aws_appsync_graphql_api.this.id
  type              = "Query"
  field             = "bookings"
  data_source       = aws_appsync_datasource.list_bookings.name
  request_template  = local.direct_lambda_request_template
  response_template = local.direct_lambda_response_template
}

resource "aws_appsync_resolver" "create_booking" {
  api_id            = aws_appsync_graphql_api.this.id
  type              = "Mutation"
  field             = "createBooking"
  data_source       = aws_appsync_datasource.create_booking.name
  request_template  = local.direct_lambda_request_template
  response_template = local.direct_lambda_response_template
}

resource "aws_appsync_resolver" "reset" {
  api_id            = aws_appsync_graphql_api.this.id
  type              = "Mutation"
  field             = "reset"
  data_source       = aws_appsync_datasource.reset.name
  request_template  = local.direct_lambda_request_template
  response_template = local.direct_lambda_response_template
}

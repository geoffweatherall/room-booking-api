locals {
  lambda_jar_path = "${path.module}/../../impl/target/room-booking-lambda.jar"
  # `terraform destroy` still evaluates this expression even though the jar's contents are
  # irrelevant when only deleting resources, so fall back to null when the jar hasn't been built
  # (e.g. undeploy.sh without ever having run deploy.sh) instead of erroring out.
  lambda_jar_hash = fileexists(local.lambda_jar_path) ? filebase64sha256(local.lambda_jar_path) : null
  lambda_env_vars = {
    ROOMS_TABLE_NAME    = aws_dynamodb_table.rooms.name
    PEOPLE_TABLE_NAME   = aws_dynamodb_table.people.name
    BOOKINGS_TABLE_NAME = aws_dynamodb_table.bookings.name
  }
}

resource "aws_lambda_function" "list_rooms" {
  function_name    = "${var.project_name}-list-rooms"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.roombooking.handler.ListRoomsHandler::handleRequest"
  runtime          = "java21"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "list_people" {
  function_name    = "${var.project_name}-list-people"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.roombooking.handler.ListPeopleHandler::handleRequest"
  runtime          = "java21"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "create_room" {
  function_name    = "${var.project_name}-create-room"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.roombooking.handler.CreateRoomHandler::handleRequest"
  runtime          = "java21"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "create_person" {
  function_name    = "${var.project_name}-create-person"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.roombooking.handler.CreatePersonHandler::handleRequest"
  runtime          = "java21"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "list_bookings" {
  function_name    = "${var.project_name}-list-bookings"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.roombooking.handler.ListBookingsHandler::handleRequest"
  runtime          = "java21"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "create_booking" {
  function_name    = "${var.project_name}-create-booking"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.roombooking.handler.CreateBookingHandler::handleRequest"
  runtime          = "java21"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "reset" {
  function_name    = "${var.project_name}-reset"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.roombooking.handler.ResetHandler::handleRequest"
  runtime          = "java21"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

locals {
  lambda_jar_path = "${path.module}/../../impl/target/room-booking-lambda.jar"
  lambda_env_vars = {
    ROOMS_TABLE_NAME  = aws_dynamodb_table.rooms.name
    PEOPLE_TABLE_NAME = aws_dynamodb_table.people.name
  }
}

resource "aws_lambda_function" "list_rooms" {
  function_name    = "${var.project_name}-list-rooms"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.roombooking.handler.ListRoomsHandler::handleRequest"
  runtime          = "java21"
  filename         = local.lambda_jar_path
  source_code_hash = filebase64sha256(local.lambda_jar_path)
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
  source_code_hash = filebase64sha256(local.lambda_jar_path)
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
  source_code_hash = filebase64sha256(local.lambda_jar_path)
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
  source_code_hash = filebase64sha256(local.lambda_jar_path)
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

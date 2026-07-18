locals {
  lambda_jar_path = "${path.module}/../../impl/target/mootmaker-api.jar"
  # `terraform destroy` still evaluates this expression even though the jar's contents are
  # irrelevant when only deleting resources, so fall back to null when the jar hasn't been built
  # (e.g. undeploy.sh without ever having run deploy.sh) instead of erroring out.
  lambda_jar_hash = fileexists(local.lambda_jar_path) ? filebase64sha256(local.lambda_jar_path) : null
  lambda_env_vars = {
    ROOMS_TABLE_NAME                = aws_dynamodb_table.rooms.name
    PEOPLE_TABLE_NAME               = aws_dynamodb_table.people.name
    MEETINGS_TABLE_NAME             = aws_dynamodb_table.meetings.name
    MEETING_PARTICIPANTS_TABLE_NAME = aws_dynamodb_table.meeting_participants.name
  }
}

resource "aws_lambda_function" "list_rooms" {
  function_name    = "${local.resource_prefix}-list-rooms"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.mootmaker.handler.ListRoomsHandler::handleRequest"
  runtime          = "java25"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "list_people" {
  function_name    = "${local.resource_prefix}-list-people"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.mootmaker.handler.ListPeopleHandler::handleRequest"
  runtime          = "java25"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "create_room" {
  function_name    = "${local.resource_prefix}-create-room"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.mootmaker.handler.CreateRoomHandler::handleRequest"
  runtime          = "java25"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "create_person" {
  function_name    = "${local.resource_prefix}-create-person"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.mootmaker.handler.CreatePersonHandler::handleRequest"
  runtime          = "java25"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "post_confirmation_create_person" {
  function_name    = "${local.resource_prefix}-post-confirmation-create-person"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.mootmaker.handler.PostConfirmationCreatePersonHandler::handleRequest"
  runtime          = "java25"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "my_person" {
  function_name    = "${local.resource_prefix}-my-person"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.mootmaker.handler.MyPersonHandler::handleRequest"
  runtime          = "java25"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "list_meetings" {
  function_name    = "${local.resource_prefix}-list-meetings"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.mootmaker.handler.ListMeetingsHandler::handleRequest"
  runtime          = "java25"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "create_meeting" {
  function_name    = "${local.resource_prefix}-create-meeting"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.mootmaker.handler.CreateMeetingHandler::handleRequest"
  runtime          = "java25"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

resource "aws_lambda_function" "reset" {
  function_name    = "${local.resource_prefix}-reset"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "com.mootmaker.handler.ResetHandler::handleRequest"
  runtime          = "java25"
  filename         = local.lambda_jar_path
  source_code_hash = local.lambda_jar_hash
  memory_size      = 512
  timeout          = 15

  environment {
    variables = local.lambda_env_vars
  }
}

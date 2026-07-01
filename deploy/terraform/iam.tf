data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda_exec" {
  name               = "${var.project_name}-lambda-exec"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "lambda_dynamodb_access" {
  statement {
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:DeleteItem",
      "dynamodb:Scan",
      "dynamodb:Query",
    ]
    resources = [
      aws_dynamodb_table.rooms.arn,
      aws_dynamodb_table.people.arn,
      aws_dynamodb_table.bookings.arn,
    ]
  }
}

resource "aws_iam_role_policy" "lambda_dynamodb_access" {
  name   = "${var.project_name}-lambda-dynamodb-access"
  role   = aws_iam_role.lambda_exec.id
  policy = data.aws_iam_policy_document.lambda_dynamodb_access.json
}

data "aws_iam_policy_document" "appsync_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["appsync.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "appsync_lambda_invoke" {
  name               = "${var.project_name}-appsync-lambda-invoke"
  assume_role_policy = data.aws_iam_policy_document.appsync_assume_role.json
}

data "aws_iam_policy_document" "appsync_invoke_lambda" {
  statement {
    actions = ["lambda:InvokeFunction"]
    resources = [
      aws_lambda_function.list_rooms.arn,
      aws_lambda_function.list_people.arn,
      aws_lambda_function.create_room.arn,
      aws_lambda_function.create_person.arn,
      aws_lambda_function.list_bookings.arn,
      aws_lambda_function.create_booking.arn,
      aws_lambda_function.reset.arn,
    ]
  }
}

resource "aws_iam_role_policy" "appsync_invoke_lambda" {
  name   = "${var.project_name}-appsync-invoke-lambda"
  role   = aws_iam_role.appsync_lambda_invoke.id
  policy = data.aws_iam_policy_document.appsync_invoke_lambda.json
}

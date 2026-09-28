# Papeis IAM do ECS.
#  - execution role: baixar a imagem do ECR e escrever logs no CloudWatch.
#  - task role: permissao de acesso as tabelas DynamoDB dos servicos.

data "aws_iam_policy_document" "ecs_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.project}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "task" {
  name               = "${var.project}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

data "aws_iam_policy_document" "dynamodb_access" {
  statement {
    effect = "Allow"
    actions = [
      "dynamodb:PutItem",
      "dynamodb:GetItem",
      "dynamodb:UpdateItem",
      "dynamodb:DeleteItem",
      "dynamodb:Query",
      "dynamodb:DescribeTable"
    ]
    resources = [
      aws_dynamodb_table.reservations.arn,
      "${aws_dynamodb_table.reservations.arn}/index/*",
      aws_dynamodb_table.matches.arn,
      aws_dynamodb_table.idempotency.arn
    ]
  }
}

resource "aws_iam_role_policy" "task_dynamodb" {
  name   = "${var.project}-dynamodb-access"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.dynamodb_access.json
}

# Cluster ECS e as duas services Fargate. Cada task roda uma copia stateless
# do servico; o reservation-service registra-se no Cloud Map e ambos ficam
# atras do ALB.

resource "aws_ecs_cluster" "main" {
  name = "${var.project}-cluster"
}

resource "aws_ecs_task_definition" "reservation" {
  family                   = "${var.project}-reservation-service"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.reservation_cpu
  memory                   = var.reservation_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "reservation-service"
      image     = "${aws_ecr_repository.reservation.repository_url}:${var.image_tag}"
      essential = true
      portMappings = [
        { containerPort = 8081, hostPort = 8081, protocol = "tcp" }
      ]
      environment = [
        { name = "AWS_REGION", value = var.aws_region },
        { name = "RESERVATION_TABLE", value = aws_dynamodb_table.reservations.name },
        { name = "IDEMPOTENCY_TABLE", value = aws_dynamodb_table.idempotency.name }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.reservation.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "reservation"
        }
      }
    }
  ])
}

resource "aws_ecs_task_definition" "match" {
  family                   = "${var.project}-match-service"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.match_cpu
  memory                   = var.match_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "match-service"
      image     = "${aws_ecr_repository.match.repository_url}:${var.image_tag}"
      essential = true
      portMappings = [
        { containerPort = 8082, hostPort = 8082, protocol = "tcp" }
      ]
      environment = [
        { name = "AWS_REGION", value = var.aws_region },
        { name = "MATCH_TABLE", value = aws_dynamodb_table.matches.name },
        # Nome logico resolvido pelo Cloud Map, sem IP fixo.
        { name = "RESERVATION_SERVICE_URL", value = "http://reservation-service.jogafacil.local:8081" }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.match.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "match"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "reservation" {
  name            = "${var.project}-reservation-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.reservation.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.reservation.arn
    container_name   = "reservation-service"
    container_port   = 8081
  }

  service_registries {
    registry_arn = aws_service_discovery_service.reservation.arn
  }

  depends_on = [aws_lb_listener.http]
}

resource "aws_ecs_service" "match" {
  name            = "${var.project}-match-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.match.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.match.arn
    container_name   = "match-service"
    container_port   = 8082
  }

  depends_on = [aws_lb_listener.http]
}

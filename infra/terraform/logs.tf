# Log groups no CloudWatch. A retencao curta (7 dias) reduz custo e ja
# fornece as metricas de requisicoes/erros exigidas nas proximas entregas.

resource "aws_cloudwatch_log_group" "reservation" {
  name              = "/ecs/${var.project}/reservation-service"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "match" {
  name              = "/ecs/${var.project}/match-service"
  retention_in_days = 7
}

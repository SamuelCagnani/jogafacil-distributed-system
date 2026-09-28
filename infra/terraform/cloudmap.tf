# AWS Cloud Map: o match-service encontra o reservation-service pelo nome
# logico reservation-service.jogafacil.local, sem IP fixo (Principio III).

resource "aws_service_discovery_private_dns_namespace" "main" {
  name        = "jogafacil.local"
  description = "Namespace de descoberta de servicos da plataforma"
  vpc         = data.aws_vpc.default.id
}

resource "aws_service_discovery_service" "reservation" {
  name = "reservation-service"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

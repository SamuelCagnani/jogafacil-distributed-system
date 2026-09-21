# Security groups.
#  - alb: aceita HTTP (80) da internet.
#  - service: aceita trafego do ALB e entre as proprias tasks (match acessa o
#    reservation-service pelo Cloud Map na porta 8081).

resource "aws_security_group" "alb" {
  name        = "${var.project}-alb"
  description = "Entrada publica HTTP para o ALB"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "HTTP publico"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Saida para as tasks"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "service" {
  name        = "${var.project}-service"
  description = "Trafego entre servicos e do ALB para as tasks"
  vpc_id      = data.aws_vpc.default.id

  # Comunicacao service-to-service (match-service -> reservation-service).
  ingress {
    description = "Servicos entre si"
    from_port   = 8081
    to_port     = 8082
    protocol    = "tcp"
    self        = true
  }

  # ALB alcanca as portas dos servicos.
  ingress {
    description     = "ALB para os servicos"
    from_port       = 8081
    to_port         = 8082
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Saida irrestrita (ECR, CloudWatch, DynamoDB)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

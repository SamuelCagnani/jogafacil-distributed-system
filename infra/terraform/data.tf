# Descobertas de recursos existentes: usamos a VPC default para manter a
# infraestrutura minima (sem NAT Gateway, que teria custo fixo).

data "aws_caller_identity" "current" {}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

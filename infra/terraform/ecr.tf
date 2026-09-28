# Repositorios ECR que guardam as imagens dos dois servicos.

resource "aws_ecr_repository" "reservation" {
  name                 = "${var.project}/reservation-service"
  image_tag_mutability = "MUTABLE"
  # Permite remover o repositorio (e as imagens) no terraform destroy.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "match" {
  name                 = "${var.project}/match-service"
  image_tag_mutability = "MUTABLE"
  # Permite remover o repositorio (e as imagens) no terraform destroy.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

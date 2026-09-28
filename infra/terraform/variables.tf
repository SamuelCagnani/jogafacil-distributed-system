variable "aws_region" {
  description = "Regiao AWS onde a plataforma e implantada"
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Prefixo usado nos nomes dos recursos"
  type        = string
  default     = "jogafacil"
}

variable "image_tag" {
  description = "Tag das imagens Docker publicadas no ECR"
  type        = string
  default     = "latest"
}

variable "reservation_cpu" {
  description = "vCPU da task do reservation-service (unidades Fargate)"
  type        = string
  default     = "256"
}

variable "reservation_memory" {
  description = "Memoria (MB) da task do reservation-service"
  type        = string
  default     = "512"
}

variable "match_cpu" {
  description = "vCPU da task do match-service (unidades Fargate)"
  type        = string
  default     = "256"
}

variable "match_memory" {
  description = "Memoria (MB) da task do match-service"
  type        = string
  default     = "512"
}

output "alb_dns_name" {
  description = "DNS publico do ALB (endpoint dos servicos)"
  value       = aws_lb.main.dns_name
}

output "reservation_base_url" {
  description = "URL base do reservation-service via ALB"
  value       = "http://${aws_lb.main.dns_name}/reservations"
}

output "match_base_url" {
  description = "URL base do match-service via ALB"
  value       = "http://${aws_lb.main.dns_name}/matches"
}

output "ecr_reservation_url" {
  description = "Repositorio ECR do reservation-service"
  value       = aws_ecr_repository.reservation.repository_url
}

output "ecr_match_url" {
  description = "Repositorio ECR do match-service"
  value       = aws_ecr_repository.match.repository_url
}

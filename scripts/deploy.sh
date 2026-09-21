#!/usr/bin/env bash
#
# Publica os servicos na AWS: cria os repositorios ECR, faz build/push das
# imagens e aplica a infraestrutura (ECS Fargate, ALB, Cloud Map, DynamoDB).
#
# Pre-requisitos:
#   - AWS CLI configurada (aws configure) com permissoes de ECS/ECR/ALB/IAM/DynamoDB
#   - Docker, Terraform e Maven instalados
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="$ROOT_DIR/infra/terraform"
AWS_REGION="${AWS_REGION:-us-east-1}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

cd "$TF_DIR"
terraform init -input=false

echo "== 1. Criando repositorios ECR =="
terraform apply -input=false -auto-approve \
  -target=aws_ecr_repository.reservation \
  -target=aws_ecr_repository.match

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
RESERVATION_IMAGE="${REGISTRY}/jogafacil/reservation-service:${IMAGE_TAG}"
MATCH_IMAGE="${REGISTRY}/jogafacil/match-service:${IMAGE_TAG}"

echo "== 2. Login no ECR =="
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"

echo "== 3. Build das imagens =="
docker build -f "$ROOT_DIR/infra/docker/Dockerfile" \
  --build-arg SERVICE=reservation-service -t "$RESERVATION_IMAGE" "$ROOT_DIR"
docker build -f "$ROOT_DIR/infra/docker/Dockerfile" \
  --build-arg SERVICE=match-service -t "$MATCH_IMAGE" "$ROOT_DIR"

echo "== 4. Push das imagens =="
docker push "$RESERVATION_IMAGE"
docker push "$MATCH_IMAGE"

echo "== 5. Aplicando a infraestrutura completa =="
terraform apply -input=false -auto-approve \
  -var="aws_region=${AWS_REGION}" -var="image_tag=${IMAGE_TAG}"

echo "== 6. Endpoints =="
terraform output

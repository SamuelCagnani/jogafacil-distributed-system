#!/bin/bash
#
# Bootstrap do LocalStack: cria as tabelas DynamoDB usadas pelos servicos.
# Executado automaticamente pelo LocalStack (volume em ready.d) quando o
# ambiente fica pronto. Idempotente: ignora tabelas ja existentes.
#
set -euo pipefail

create_table() {
  if awslocal dynamodb describe-table --table-name "$1" >/dev/null 2>&1; then
    echo "[bootstrap] tabela $1 ja existe"
  else
    shift
    awslocal dynamodb create-table "$@"
    echo "[bootstrap] tabela criada"
  fi
}

# Reservas: chave (courtId, slotId) garante uma reserva por horario de quadra.
# GSI byReservationId permite buscar pelo id gerado.
create_table jogafacil-reservations \
  --table-name jogafacil-reservations \
  --attribute-definitions \
      AttributeName=courtId,AttributeType=S \
      AttributeName=slotId,AttributeType=S \
      AttributeName=reservationId,AttributeType=S \
  --key-schema \
      AttributeName=courtId,KeyType=HASH \
      AttributeName=slotId,KeyType=RANGE \
  --global-secondary-indexes \
      'IndexName=byReservationId,KeySchema=[{AttributeName=reservationId,KeyType=HASH}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST

# Partidas: chave matchId.
create_table jogafacil-matches \
  --table-name jogafacil-matches \
  --attribute-definitions AttributeName=matchId,AttributeType=S \
  --key-schema AttributeName=matchId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

# Idempotencia: chave operationId (Idempotency-Key) com expiracao por TTL.
create_table jogafacil-idempotency \
  --table-name jogafacil-idempotency \
  --attribute-definitions AttributeName=operationId,AttributeType=S \
  --key-schema AttributeName=operationId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

# Habilita TTL para que os registros de idempotencia expirem automaticamente.
awslocal dynamodb update-time-to-live \
  --table-name jogafacil-idempotency \
  --time-to-live-specification Enabled=true,AttributeName=ttl >/dev/null

echo "[bootstrap] tabelas prontas"

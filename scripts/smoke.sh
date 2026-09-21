#!/usr/bin/env bash
#
# Teste de fumaca ponta a ponta do fluxo de comunicacao entre os servicos.
#
# Demonstra: criar partida reserva o horario via REST no reservation-service,
# um segundo pedido para o mesmo horario retorna 409, cancelar libera o horario
# e um novo pedido volta a ser aceito.
#
# Pre-requisitos: os dois servicos rodando (docker compose ou local).
#
set -euo pipefail

RESERVATION_URL="${RESERVATION_URL:-http://localhost:8081}"
MATCH_URL="${MATCH_URL:-http://localhost:8082}"

COURT_ID="${COURT_ID:-court_01ARZ3NDEKTSV4RRFFQ69G5FAV}"
SLOT_ID="${SLOT_ID:-slot_01ARZ3NDEKTSV4RRFFQ69G5FB1}"
ORGANIZER_ID="${ORGANIZER_ID:-usr_01ARZ3NDEKTSV4RRFFQ69G5FAV}"

MEDIA_TYPE="application/vnd.jogafacil.v1+json"
PASS=0
FAIL=0

check() {
  local description="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "  [OK]   $description (HTTP $actual)"
    PASS=$((PASS + 1))
  else
    echo "  [FAIL] $description: esperado HTTP $expected, obtido HTTP $actual"
    FAIL=$((FAIL + 1))
  fi
}

wait_for_health() {
  local name="$1" url="$2"
  for _ in $(seq 1 60); do
    if curl -fsS "$url/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "  $name pronto"
      return 0
    fi
    sleep 2
  done
  echo "  $name nao ficou UP a tempo"
  exit 1
}

create_match() {
  local idempotency_key="$1" output_file="$2"
  curl -s -o "$output_file" -w '%{http_code}' -X POST "$MATCH_URL/matches" \
    -H "Content-Type: $MEDIA_TYPE" \
    -H "Accept: $MEDIA_TYPE" \
    -H "Idempotency-Key: $idempotency_key" \
    -d "{\"courtId\":\"$COURT_ID\",\"slotId\":\"$SLOT_ID\",\"organizerId\":\"$ORGANIZER_ID\",\"maxParticipants\":10}"
}

if [ "${SKIP_HEALTH_WAIT:-0}" != "1" ]; then
  echo "== Aguardando servicos =="
  wait_for_health "reservation-service" "$RESERVATION_URL"
  wait_for_health "match-service" "$MATCH_URL"
else
  echo "== Espera de health ignorada (SKIP_HEALTH_WAIT=1) =="
fi

echo "== 1. POST /matches cria a partida e reserva o horario =="
STATUS=""
for attempt in $(seq 1 30); do
  STATUS=$(create_match "smoke-create" /tmp/smoke-match-1.json)
  if [ "$STATUS" != "500" ] && [ "$STATUS" != "503" ]; then
    break
  fi
  echo "  servico ainda inicializando (HTTP $STATUS), tentativa $attempt..."
  sleep 2
done
check "cria partida" 201 "$STATUS"
RESERVATION_ID=$(python3 -c "import json;print(json.load(open('/tmp/smoke-match-1.json'))['reservationId'])")
echo "  reservationId=$RESERVATION_ID"

echo "== 2. Segundo POST /matches para o mesmo horario e rejeitado =="
STATUS=$(create_match "smoke-conflict" /tmp/smoke-match-2.json)
check "conflito de horario (RESERVATION_CONFLICT)" 409 "$STATUS"
CODE=$(python3 -c "import json;print(json.load(open('/tmp/smoke-match-2.json'))['code'])")
echo "  code=$CODE"

echo "== 3. GET /reservations/{id} devolve a reserva =="
STATUS=$(curl -s -o /tmp/smoke-reservation.json -w '%{http_code}' \
  -H "Accept: $MEDIA_TYPE" "$RESERVATION_URL/reservations/$RESERVATION_ID")
check "consulta reserva" 200 "$STATUS"

echo "== 4. DELETE /reservations/{id} cancela e libera o horario =="
STATUS=$(curl -s -o /tmp/smoke-cancel.json -w '%{http_code}' -X DELETE \
  -H "Accept: $MEDIA_TYPE" "$RESERVATION_URL/reservations/$RESERVATION_ID")
check "cancela reserva" 200 "$STATUS"

echo "== 5. Novo POST /matches reutiliza o horario liberado =="
STATUS=$(create_match "smoke-reuse" /tmp/smoke-match-3.json)
check "reutiliza horario liberado" 201 "$STATUS"

echo
echo "Resultado: $PASS OK, $FAIL FAIL"
test "$FAIL" -eq 0

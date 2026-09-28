# Tabelas DynamoDB. A de reservas usa chave (courtId, slotId) e o GSI
# byReservationId; a de idempotencia usa TTL de 24h; a de partidas usa matchId.

resource "aws_dynamodb_table" "reservations" {
  name         = "jogafacil-reservations"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "courtId"
  range_key    = "slotId"

  attribute {
    name = "courtId"
    type = "S"
  }

  attribute {
    name = "slotId"
    type = "S"
  }

  attribute {
    name = "reservationId"
    type = "S"
  }

  global_secondary_index {
    name            = "byReservationId"
    hash_key        = "reservationId"
    projection_type = "ALL"
  }
}

resource "aws_dynamodb_table" "matches" {
  name         = "jogafacil-matches"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "matchId"

  attribute {
    name = "matchId"
    type = "S"
  }
}

resource "aws_dynamodb_table" "idempotency" {
  name         = "jogafacil-idempotency"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "operationId"

  attribute {
    name = "operationId"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }
}

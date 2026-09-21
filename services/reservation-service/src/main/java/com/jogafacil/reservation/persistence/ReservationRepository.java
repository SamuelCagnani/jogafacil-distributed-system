package com.jogafacil.reservation.persistence;

import com.jogafacil.reservation.domain.Reservation;
import com.jogafacil.reservation.domain.ReservationStatus;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Persistencia das reservas em DynamoDB.
 *
 * <p>O ponto central do controle de concorrencia esta em
 * {@link #saveIfSlotFree(Reservation)}: o {@code PutItem} usa a condicao
 * {@code attribute_not_exists(courtId) OR status = CANCELLED}, avaliada
 * atomicamente pelo DynamoDB. Assim, de N tentativas simultaneas para o mesmo
 * {@code (courtId, slotId)}, no maximo uma e confirmada; as demais recebem
 * {@code ConditionalCheckFailedException}. Uma reserva cancelada nao bloqueia o
 * horario, pois a condicao permite sobrescreve-la.</p>
 *
 * <p>A tabela tem chave primaria {@code courtId} (HASH) + {@code slotId} (RANGE)
 * e o indice global secundario {@code byReservationId} para buscar pelo id.</p>
 */
public class ReservationRepository {

    private static final String ATTR_COURT_ID = "courtId";
    private static final String ATTR_SLOT_ID = "slotId";
    private static final String ATTR_RESERVATION_ID = "reservationId";
    private static final String ATTR_OWNER_ID = "ownerId";
    private static final String ATTR_MATCH_ID = "matchId";
    private static final String ATTR_STATUS = "status";
    private static final String ATTR_CREATED_AT = "createdAt";
    private static final String ATTR_CANCELLED_AT = "cancelledAt";

    private final DynamoDbClient client;
    private final String tableName;
    private final String reservationIdIndex;

    public ReservationRepository(DynamoDbClient client, String tableName, String reservationIdIndex) {
        this.client = client;
        this.tableName = tableName;
        this.reservationIdIndex = reservationIdIndex;
    }

    /**
     * Grava a reserva somente se o horario estiver livre.
     *
     * @return {@code true} se a reserva foi confirmada; {@code false} se o
     *         horario ja possui uma reserva ativa (conflito)
     */
    public boolean saveIfSlotFree(Reservation reservation) {
        try {
            client.putItem(builder -> builder
                    .tableName(tableName)
                    .item(toItem(reservation))
                    .conditionExpression("attribute_not_exists(" + ATTR_COURT_ID + ") OR #status = :cancelled")
                    .expressionAttributeNames(Map.of("#status", ATTR_STATUS))
                    .expressionAttributeValues(Map.of(":cancelled",
                            AttributeValue.fromS(ReservationStatus.CANCELLED.name()))));
            return true;
        } catch (ConditionalCheckFailedException conflict) {
            return false;
        }
    }

    /**
     * Cancela a reserva de forma idempotente.
     *
     * @return {@code true} se havia uma reserva ativa para cancelar;
     *         {@code false} se ja estava cancelada ou nao existe
     */
    public boolean cancel(Reservation reservation) {
        try {
            client.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key(reservation.courtId(), reservation.slotId()))
                    .updateExpression("SET #status = :cancelled, " + ATTR_CANCELLED_AT + " = :cancelledAt")
                    .conditionExpression("#status = :active")
                    .expressionAttributeNames(Map.of("#status", ATTR_STATUS))
                    .expressionAttributeValues(Map.of(
                            ":cancelled", AttributeValue.fromS(ReservationStatus.CANCELLED.name()),
                            ":active", AttributeValue.fromS(ReservationStatus.ACTIVE.name()),
                            ":cancelledAt", AttributeValue.fromS(Instant.now().toString())))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException alreadyCancelledOrMissing) {
            return false;
        }
    }

    public Optional<Reservation> findById(String reservationId) {
        return client.queryPaginator(QueryRequest.builder()
                        .tableName(tableName)
                        .indexName(reservationIdIndex)
                        .keyConditionExpression(ATTR_RESERVATION_ID + " = :reservationId")
                        .expressionAttributeValues(Map.of(":reservationId", AttributeValue.fromS(reservationId)))
                        .limit(1)
                        .build())
                .items()
                .stream()
                .findFirst()
                .map(this::fromItem);
    }

    public Optional<Reservation> findByCourtAndSlot(String courtId, String slotId) {
        Map<String, AttributeValue> item = client.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(key(courtId, slotId))
                        .build())
                .item();
        return item.isEmpty() ? Optional.empty() : Optional.of(fromItem(item));
    }

    private static Map<String, AttributeValue> key(String courtId, String slotId) {
        return Map.of(
                ATTR_COURT_ID, AttributeValue.fromS(courtId),
                ATTR_SLOT_ID, AttributeValue.fromS(slotId));
    }

    private static Map<String, AttributeValue> toItem(Reservation reservation) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(ATTR_COURT_ID, AttributeValue.fromS(reservation.courtId()));
        item.put(ATTR_SLOT_ID, AttributeValue.fromS(reservation.slotId()));
        item.put(ATTR_RESERVATION_ID, AttributeValue.fromS(reservation.reservationId()));
        item.put(ATTR_OWNER_ID, AttributeValue.fromS(reservation.ownerId()));
        item.put(ATTR_STATUS, AttributeValue.fromS(reservation.status().name()));
        item.put(ATTR_CREATED_AT, AttributeValue.fromS(reservation.createdAt().toString()));
        if (reservation.matchId() != null) {
            item.put(ATTR_MATCH_ID, AttributeValue.fromS(reservation.matchId()));
        }
        if (reservation.cancelledAt() != null) {
            item.put(ATTR_CANCELLED_AT, AttributeValue.fromS(reservation.cancelledAt().toString()));
        }
        return item;
    }

    private Reservation fromItem(Map<String, AttributeValue> item) {
        return new Reservation(
                item.get(ATTR_RESERVATION_ID).s(),
                item.get(ATTR_COURT_ID).s(),
                item.get(ATTR_SLOT_ID).s(),
                item.get(ATTR_OWNER_ID).s(),
                item.containsKey(ATTR_MATCH_ID) ? item.get(ATTR_MATCH_ID).s() : null,
                ReservationStatus.valueOf(item.get(ATTR_STATUS).s()),
                Instant.parse(item.get(ATTR_CREATED_AT).s()),
                item.containsKey(ATTR_CANCELLED_AT) ? Instant.parse(item.get(ATTR_CANCELLED_AT).s()) : null);
    }
}

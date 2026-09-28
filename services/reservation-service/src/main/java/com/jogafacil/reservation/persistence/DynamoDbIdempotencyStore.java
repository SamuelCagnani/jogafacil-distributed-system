package com.jogafacil.reservation.persistence;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Implementacao do {@link IdempotencyStore} em DynamoDB.
 *
 * <p>Usa a tabela de chave unica {@code operationId}. A reserva da chave e uma
 * escrita condicional ({@code attribute_not_exists(operationId)}), portanto
 * apenas a primeira requisicao com dada chave prossegue. Registros expiram por
 * TTL (24h), mantendo a tabela limitada.</p>
 */
public class DynamoDbIdempotencyStore implements IdempotencyStore {

    private static final String ATTR_OPERATION_ID = "operationId";
    private static final String ATTR_REQUEST_HASH = "requestHash";
    private static final String ATTR_RESPONSE_STATUS = "responseStatus";
    private static final String ATTR_RESPONSE_BODY = "responseBody";
    private static final String ATTR_TTL = "ttl";

    private static final Duration RETENTION = Duration.ofHours(24);

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbIdempotencyStore(DynamoDbClient client, String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    @Override
    public Optional<IdempotencyRecord> find(String operationId) {
        Map<String, AttributeValue> item = client.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(ATTR_OPERATION_ID, AttributeValue.fromS(operationId)))
                        .build())
                .item();
        if (item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new IdempotencyRecord(
                operationId,
                item.get(ATTR_REQUEST_HASH).s(),
                item.containsKey(ATTR_RESPONSE_STATUS) ? Integer.valueOf(item.get(ATTR_RESPONSE_STATUS).n()) : null,
                item.containsKey(ATTR_RESPONSE_BODY) ? item.get(ATTR_RESPONSE_BODY).s() : null));
    }

    @Override
    public boolean claim(String operationId, String requestHash) {
        try {
            client.putItem(builder -> builder
                    .tableName(tableName)
                    .item(Map.of(
                            ATTR_OPERATION_ID, AttributeValue.fromS(operationId),
                            ATTR_REQUEST_HASH, AttributeValue.fromS(requestHash),
                            ATTR_TTL, AttributeValue.fromN(Long.toString(
                                    Instant.now().plus(RETENTION).getEpochSecond()))))
                    .conditionExpression("attribute_not_exists(" + ATTR_OPERATION_ID + ")"));
            return true;
        } catch (ConditionalCheckFailedException alreadyClaimed) {
            return false;
        }
    }

    @Override
    public void complete(String operationId, int responseStatus, String responseBody) {
        client.updateItem(builder -> builder
                .tableName(tableName)
                .key(Map.of(ATTR_OPERATION_ID, AttributeValue.fromS(operationId)))
                .updateExpression("SET " + ATTR_RESPONSE_STATUS + " = :status, " + ATTR_RESPONSE_BODY + " = :body")
                .conditionExpression("attribute_exists(" + ATTR_OPERATION_ID + ")")
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.fromN(Integer.toString(responseStatus)),
                        ":body", AttributeValue.fromS(responseBody))));
    }

    @Override
    public void release(String operationId) {
        try {
            client.deleteItem(builder -> builder
                    .tableName(tableName)
                    .key(Map.of(ATTR_OPERATION_ID, AttributeValue.fromS(operationId)))
                    .conditionExpression("attribute_exists(" + ATTR_OPERATION_ID + ")"));
        } catch (ConditionalCheckFailedException alreadyReleased) {
            // nada a fazer: a chave ja nao estava reservada
        }
    }
}

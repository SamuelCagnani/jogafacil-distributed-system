package com.jogafacil.reservation.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova o ciclo de idempotencia contra um DynamoDB real (LocalStack): a chave
 * so pode ser reservada uma vez, a resposta e gravada e a liberacao permite
 * nova tentativa.
 */
@Testcontainers
class DynamoDbIdempotencyStoreIT {

    private static final String TABLE = "jogafacil-idempotency-test";

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7.2"))
                    .withServices(LocalStackContainer.Service.DYNAMODB);

    private static DynamoDbClient client;
    private DynamoDbIdempotencyStore store;
    private String operationId;

    @BeforeAll
    static void createTable() {
        client = DynamoDbClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();

        client.createTable(CreateTableRequest.builder()
                .tableName(TABLE)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName("operationId")
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .keySchema(KeySchemaElement.builder()
                        .attributeName("operationId")
                        .keyType(KeyType.HASH)
                        .build())
                .build());
    }

    @BeforeEach
    void createStore() {
        store = new DynamoDbIdempotencyStore(client, TABLE);
        operationId = "op-" + UUID.randomUUID();
    }

    @Test
    void claimsAKeyOnlyOnce() {
        assertThat(store.claim(operationId, "hash-1")).isTrue();
        assertThat(store.claim(operationId, "hash-1")).isFalse();
    }

    @Test
    void storesAndReadsBackTheCompletedOutcome() {
        store.claim(operationId, "hash-1");
        store.complete(operationId, 201, "{\"reservationId\":\"res_x\"}");

        assertThat(store.find(operationId))
                .get()
                .satisfies(record -> {
                    assertThat(record.requestHash()).isEqualTo("hash-1");
                    assertThat(record.responseStatus()).isEqualTo(201);
                    assertThat(record.responseBody()).isEqualTo("{\"reservationId\":\"res_x\"}");
                });
    }

    @Test
    void releaseAllowsANewAttempt() {
        store.claim(operationId, "hash-1");
        store.release(operationId);

        assertThat(store.find(operationId)).isEmpty();
        assertThat(store.claim(operationId, "hash-2")).isTrue();
    }
}

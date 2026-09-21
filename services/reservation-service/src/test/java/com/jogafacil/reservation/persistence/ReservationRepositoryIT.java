package com.jogafacil.reservation.persistence;

import com.jogafacil.contracts.id.UlidGenerator;
import com.jogafacil.reservation.domain.Reservation;
import com.jogafacil.reservation.domain.ReservationStatus;
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
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um DynamoDB real (LocalStack), que a escrita condicional
 * garante no maximo uma reserva por {@code (courtId, slotId)} e que o
 * cancelamento libera o horario.
 */
@Testcontainers
class ReservationRepositoryIT {

    private static final String TABLE = "jogafacil-reservations-test";
    private static final String IDEMPOTENCY_INDEX = "byReservationId";

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7.2"))
                    .withServices(LocalStackContainer.Service.DYNAMODB);

    private static DynamoDbClient client;
    private ReservationRepository repository;

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
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("courtId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("slotId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("reservationId").attributeType(ScalarAttributeType.S).build())
                .keySchema(
                        KeySchemaElement.builder().attributeName("courtId").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("slotId").keyType(KeyType.RANGE).build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName(IDEMPOTENCY_INDEX)
                        .keySchema(KeySchemaElement.builder().attributeName("reservationId").keyType(KeyType.HASH).build())
                        .projection(projection -> projection.projectionType(ProjectionType.ALL))
                        .build())
                .build());
    }

    @BeforeEach
    void createRepository() {
        repository = new ReservationRepository(client, TABLE, IDEMPOTENCY_INDEX);
    }

    @Test
    void storesOnlyOneReservationPerCourtAndSlot() {
        Reservation first = activeReservation();
        Reservation second = activeReservation(first.courtId(), first.slotId());

        assertThat(repository.saveIfSlotFree(first)).isTrue();
        assertThat(repository.saveIfSlotFree(second)).isFalse();

        assertThat(repository.findById(first.reservationId())).isPresent();
        assertThat(repository.findById(second.reservationId())).isEmpty();
    }

    @Test
    void releasesTheSlotWhenTheReservationIsCancelled() {
        Reservation first = activeReservation();
        assertThat(repository.saveIfSlotFree(first)).isTrue();

        assertThat(repository.cancel(first)).isTrue();
        assertThat(repository.findById(first.reservationId()))
                .get()
                .extracting(Reservation::status)
                .isEqualTo(ReservationStatus.CANCELLED);

        Reservation replacement = activeReservation(first.courtId(), first.slotId());
        assertThat(repository.saveIfSlotFree(replacement)).isTrue();
        assertThat(repository.findById(replacement.reservationId())).isPresent();
    }

    private static Reservation activeReservation() {
        return activeReservation(
                UlidGenerator.generate(UlidGenerator.COURT_PREFIX),
                UlidGenerator.generate(UlidGenerator.SLOT_PREFIX));
    }

    private static Reservation activeReservation(String courtId, String slotId) {
        return new Reservation(
                UlidGenerator.generate(UlidGenerator.RESERVATION_PREFIX),
                courtId,
                slotId,
                UlidGenerator.generate(UlidGenerator.USER_PREFIX),
                null,
                ReservationStatus.ACTIVE,
                Instant.now(),
                null);
    }
}

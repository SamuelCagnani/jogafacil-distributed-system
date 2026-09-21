package com.jogafacil.match.persistence;

import com.jogafacil.contracts.id.UlidGenerator;
import com.jogafacil.match.domain.Match;
import com.jogafacil.match.domain.MatchStatus;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova a persistencia de partidas contra um DynamoDB real (LocalStack).
 */
@Testcontainers
class MatchRepositoryIT {

    private static final String TABLE = "jogafacil-matches-test";

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7.2"))
                    .withServices(LocalStackContainer.Service.DYNAMODB);

    private static DynamoDbClient client;
    private MatchRepository repository;

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
                        .attributeName("matchId")
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .keySchema(KeySchemaElement.builder()
                        .attributeName("matchId")
                        .keyType(KeyType.HASH)
                        .build())
                .build());
    }

    @BeforeEach
    void createRepository() {
        repository = new MatchRepository(client, TABLE);
    }

    @Test
    void storesAndReadsBackAMatch() {
        Match match = new Match(
                UlidGenerator.generate(UlidGenerator.MATCH_PREFIX),
                UlidGenerator.generate(UlidGenerator.COURT_PREFIX),
                UlidGenerator.generate(UlidGenerator.SLOT_PREFIX),
                UlidGenerator.generate(UlidGenerator.USER_PREFIX),
                UlidGenerator.generate(UlidGenerator.RESERVATION_PREFIX),
                10, 0, MatchStatus.OPEN, Instant.now());

        repository.save(match);

        assertThat(repository.findById(match.matchId())).contains(match);
    }

    @Test
    void returnsEmptyForAnUnknownMatch() {
        assertThat(repository.findById(UlidGenerator.generate(UlidGenerator.MATCH_PREFIX))).isEmpty();
    }
}

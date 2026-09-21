package com.jogafacil.reservation.config;

import com.jogafacil.reservation.persistence.DynamoDbIdempotencyStore;
import com.jogafacil.reservation.persistence.IdempotencyStore;
import com.jogafacil.reservation.persistence.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * Monta o cliente AWS e os repositorios DynamoDB.
 *
 * <p>No perfil {@code local} o endpoint aponta para o LocalStack e credenciais
 * fixas de teste sao usadas; no perfil {@code aws} (default) o
 * {@link DefaultCredentialsProvider} usa a task role do ECS Fargate.</p>
 */
@Configuration
public class AwsConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(
            @Value("${aws.region}") String region,
            @Value("${aws.dynamodb.endpoint:}") String endpoint,
            @Value("${aws.access-key-id:}") String accessKeyId,
            @Value("${aws.secret-access-key:}") String secretAccessKey) {

        var builder = DynamoDbClient.builder().region(Region.of(region));

        if (StringUtils.hasText(accessKeyId)) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    public ReservationRepository reservationRepository(DynamoDbClient client, ReservationProperties properties) {
        return new ReservationRepository(client, properties.reservationTable(), properties.reservationIdIndex());
    }

    @Bean
    public IdempotencyStore idempotencyStore(DynamoDbClient client, ReservationProperties properties) {
        return new DynamoDbIdempotencyStore(client, properties.idempotencyTable());
    }
}

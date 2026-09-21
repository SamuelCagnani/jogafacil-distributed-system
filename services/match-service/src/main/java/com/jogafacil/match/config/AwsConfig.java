package com.jogafacil.match.config;

import com.jogafacil.match.persistence.MatchRepository;
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
 * Monta o cliente AWS e o repositorio de partidas.
 *
 * <p>Perfil {@code local}: endpoint no LocalStack e credenciais de teste.
 * Perfil {@code aws} (default): {@link DefaultCredentialsProvider} usa a task
 * role do ECS Fargate.</p>
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
    public MatchRepository matchRepository(DynamoDbClient client, MatchProperties properties) {
        return new MatchRepository(client, properties.matchTable());
    }
}

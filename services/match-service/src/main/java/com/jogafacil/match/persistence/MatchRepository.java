package com.jogafacil.match.persistence;

import com.jogafacil.match.domain.Match;
import com.jogafacil.match.domain.MatchStatus;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Persistencia das partidas em DynamoDB (chave primaria {@code matchId}).
 */
public class MatchRepository {

    private static final String ATTR_MATCH_ID = "matchId";
    private static final String ATTR_COURT_ID = "courtId";
    private static final String ATTR_SLOT_ID = "slotId";
    private static final String ATTR_ORGANIZER_ID = "organizerId";
    private static final String ATTR_RESERVATION_ID = "reservationId";
    private static final String ATTR_MAX_PARTICIPANTS = "maxParticipants";
    private static final String ATTR_PARTICIPANT_COUNT = "participantCount";
    private static final String ATTR_STATUS = "status";
    private static final String ATTR_CREATED_AT = "createdAt";

    private final DynamoDbClient client;
    private final String tableName;

    public MatchRepository(DynamoDbClient client, String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    public void save(Match match) {
        client.putItem(builder -> builder
                .tableName(tableName)
                .item(toItem(match)));
    }

    public Optional<Match> findById(String matchId) {
        Map<String, AttributeValue> item = client.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(ATTR_MATCH_ID, AttributeValue.fromS(matchId)))
                        .build())
                .item();
        return item.isEmpty() ? Optional.empty() : Optional.of(fromItem(item));
    }

    private static Map<String, AttributeValue> toItem(Match match) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(ATTR_MATCH_ID, AttributeValue.fromS(match.matchId()));
        item.put(ATTR_COURT_ID, AttributeValue.fromS(match.courtId()));
        item.put(ATTR_SLOT_ID, AttributeValue.fromS(match.slotId()));
        item.put(ATTR_ORGANIZER_ID, AttributeValue.fromS(match.organizerId()));
        item.put(ATTR_RESERVATION_ID, AttributeValue.fromS(match.reservationId()));
        item.put(ATTR_MAX_PARTICIPANTS, AttributeValue.fromN(Integer.toString(match.maxParticipants())));
        item.put(ATTR_PARTICIPANT_COUNT, AttributeValue.fromN(Integer.toString(match.participantCount())));
        item.put(ATTR_STATUS, AttributeValue.fromS(match.status().name()));
        item.put(ATTR_CREATED_AT, AttributeValue.fromS(match.createdAt().toString()));
        return item;
    }

    private Match fromItem(Map<String, AttributeValue> item) {
        return new Match(
                item.get(ATTR_MATCH_ID).s(),
                item.get(ATTR_COURT_ID).s(),
                item.get(ATTR_SLOT_ID).s(),
                item.get(ATTR_ORGANIZER_ID).s(),
                item.get(ATTR_RESERVATION_ID).s(),
                Integer.parseInt(item.get(ATTR_MAX_PARTICIPANTS).n()),
                Integer.parseInt(item.get(ATTR_PARTICIPANT_COUNT).n()),
                MatchStatus.valueOf(item.get(ATTR_STATUS).s()),
                Instant.parse(item.get(ATTR_CREATED_AT).s()));
    }
}

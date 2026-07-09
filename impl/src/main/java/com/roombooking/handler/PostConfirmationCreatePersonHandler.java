package com.roombooking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.roombooking.dynamo.DynamoDbClientProvider;
import com.roombooking.dynamo.PersonRepository;
import com.roombooking.model.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import module java.base;

/**
 * Cognito PostConfirmation trigger: creates a Person linked to the newly confirmed user via
 * {@code cognitoSub}, so a future account-deletion flow can find and remove it. Unlike the
 * AppSync resolver handlers in this package, Cognito requires trigger Lambdas to return the event
 * unmodified, and treats a thrown exception as a failure of the confirm-sign-up call itself even
 * though the account is already confirmed by the time this trigger runs - so failures here are
 * logged and swallowed rather than thrown, to avoid blocking sign-up over a Person-creation
 * problem.
 */
public class PostConfirmationCreatePersonHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostConfirmationCreatePersonHandler.class);
    private static final String TRIGGER_SOURCE_CONFIRM_SIGN_UP = "PostConfirmation_ConfirmSignUp";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public PostConfirmationCreatePersonHandler() {
        this(DynamoDbClientProvider.client(), System.getenv().getOrDefault("PEOPLE_TABLE_NAME", "People"));
    }

    PostConfirmationCreatePersonHandler(final DynamoDbClient dynamoDbClient, final String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public Map<String, Object> handleRequest(final Map<String, Object> event, final Context context) {
        try {
            createPersonIfNeeded(event);
        } catch (final RuntimeException e) {
            // The name isn't known yet if extracting it from the event is itself what failed;
            // createPersonIfNeeded logs the name-specific outcome for every other failure.
            LOGGER.error("Failed to create Person for confirmed sign-up", e);
        }
        return event;
    }

    private void createPersonIfNeeded(final Map<String, Object> event) {
        if (!TRIGGER_SOURCE_CONFIRM_SIGN_UP.equals(event.get("triggerSource"))) {
            return;
        }

        final Map<String, Object> request = castToMap(event.get("request"));
        final Map<String, Object> userAttributes = castToMap(request.get("userAttributes"));
        final String cognitoSub = (String) userAttributes.get("sub");
        final String name = (String) userAttributes.get("name");

        try {
            if (PersonRepository.findByCognitoSub(dynamoDbClient, tableName, cognitoSub).isPresent()) {
                LOGGER.info("Person already exists for confirmed sign-up '{}', skipping creation", name);
                return;
            }

            final Person person = new Person(UUID.randomUUID().toString(), name, cognitoSub);
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(person.toItem())
                    .build());

            LOGGER.info("Created Person for confirmed sign-up '{}'", name);
        } catch (final RuntimeException e) {
            LOGGER.error("Failed to create Person for confirmed sign-up '{}'", name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(final Object value) {
        return (Map<String, Object>) value;
    }
}

package com.roombooking.verify;

import com.fasterxml.jackson.databind.JsonNode;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/** Acceptance test for creating a person and reading it back via the {@code people} query. */
class CreatePersonAcceptanceIT {

    private static final Logger LOG = LoggerFactory.getLogger(CreatePersonAcceptanceIT.class);

    private static GraphQlClient client;
    private static Faker faker;

    @BeforeAll
    static void setUpClient() {
        client = GraphQlClient.fromEnvironment();
        faker = new Faker();
    }

    @Test
    void createdPersonIsReturnedByPeopleQuery() {
        LOG.info("Resetting the database before the test");
        client.execute("mutation { reset }");

        final String personName = faker.name().fullName();
        LOG.info("Creating person '{}'", personName);
        final JsonNode createResult = client.execute(
                "mutation CreatePerson($person: PersonInput!) { createPerson(person: $person) { id name } }",
                Map.of("person", Map.of("name", personName)));

        final String createdId = createResult.get("createPerson").get("id").asText();
        LOG.info("Created person with id '{}'", createdId);
        assertThat(createResult.get("createPerson").get("name").asText(), equalTo(personName));

        LOG.info("Querying people to check the created person is returned");
        final JsonNode peopleResult = client.execute("query { people { id name } }");
        final JsonNode people = peopleResult.get("people");

        assertThat(people.size(), equalTo(1));
        assertThat(people.get(0).get("id").asText(), equalTo(createdId));
        assertThat(people.get(0).get("name").asText(), equalTo(personName));
        LOG.info("Person '{}' was successfully returned by the people query", personName);
    }
}

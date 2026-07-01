package com.roombooking.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import module java.base;
import module java.net.http;

/** Minimal HTTP client for executing GraphQL operations against the deployed AppSync API. */
class GraphQlClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI endpoint;
    private final String apiKey;

    GraphQlClient(final String endpoint, final String apiKey) {
        this.endpoint = URI.create(endpoint);
        this.apiKey = apiKey;
    }

    /**
     * Reads the deployed API's endpoint/key from the GRAPHQL_API_URL / GRAPHQL_API_KEY environment
     * variables, e.g.:
     *   export GRAPHQL_API_URL=$(terraform -chdir=deploy/terraform output -raw graphql_api_url)
     *   export GRAPHQL_API_KEY=$(terraform -chdir=deploy/terraform output -raw graphql_api_key)
     */
    static GraphQlClient fromEnvironment() {
        return new GraphQlClient(requireEnv("GRAPHQL_API_URL"), requireEnv("GRAPHQL_API_KEY"));
    }

    private static String requireEnv(final String name) {
        final String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required to run acceptance tests "
                    + "against the deployed room-booking API. See GraphQlClient.fromEnvironment() for how to set it.");
        }
        return value;
    }

    JsonNode execute(final String query) {
        return execute(query, Map.of());
    }

    JsonNode execute(final String query, final Map<String, Object> variables) {
        try {
            final String requestBody = OBJECT_MAPPER.writeValueAsString(Map.of("query", query, "variables", variables));

            final HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            final JsonNode root = OBJECT_MAPPER.readTree(response.body());

            if (root.has("errors")) {
                throw new IllegalStateException("GraphQL request failed: " + root.get("errors"));
            }
            return root.get("data");
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to execute GraphQL request", e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GraphQL request was interrupted", e);
        }
    }
}

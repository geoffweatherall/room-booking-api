package com.mootmaker.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import module java.base;

/**
 * {@code cognitoSub} is null for people added directly (e.g. guests with no login), and set to
 * the Cognito user's {@code sub} for people created by the PostConfirmation sign-up trigger, so a
 * future account-deletion flow can find and remove the Person linked to a deleted Cognito user.
 * It is a backend-only linking attribute, never exposed over GraphQL.
 */
public record Person(String id, String name, String cognitoSub) {

    public Person(final String id, final String name) {
        this(id, name, null);
    }

    public Map<String, AttributeValue> toItem() {
        final Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("name", AttributeValue.builder().s(name).build());
        if (cognitoSub != null) {
            item.put("cognitoSub", AttributeValue.builder().s(cognitoSub).build());
        }
        return item;
    }

    public Map<String, Object> toResponseMap() {
        final Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        return map;
    }

    public static Person fromItem(final Map<String, AttributeValue> item) {
        final AttributeValue cognitoSub = item.get("cognitoSub");
        return new Person(item.get("id").s(), item.get("name").s(), cognitoSub != null ? cognitoSub.s() : null);
    }
}

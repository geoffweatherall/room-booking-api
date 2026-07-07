package com.roombooking.handler;

import module java.base;

/**
 * Defence-in-depth authentication check. AppSync's Cognito user-pool authoriser validates the
 * caller's JWT before invoking any resolver, and puts the token's claims in the {@code identity}
 * field of the context it forwards to the Lambda. Every handler re-checks that the identity is
 * present before running any logic, so a mis-configured resolver (e.g. an accidentally public
 * API) still refuses to do work.
 */
final class Identity {

    private Identity() {
    }

    /** Throws if the AppSync context has no authenticated identity; call before any handler logic. */
    static void requireAuthenticated(final Map<String, Object> event) {
        if (event == null || event.get("identity") == null) {
            throw new IllegalStateException("Unauthorized: request has no authenticated identity");
        }
    }
}

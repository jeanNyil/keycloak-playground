package io.jeannyil;

import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store keyed by a cookie value.
 * Replaces browser localStorage from the vanilla JS version.
 * Sessions expire after 1 hour of inactivity.
 */
@Singleton
public class OAuthSessionStore {

    private static final Duration SESSION_TTL = Duration.ofHours(1);
    private static final int MAX_SESSIONS = 1000;

    private final ConcurrentHashMap<String, OAuthFlowState> sessions = new ConcurrentHashMap<>();

    public String createSession() {
        evictExpired();
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new OAuthFlowState());
        return sessionId;
    }

    public OAuthFlowState getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        OAuthFlowState state = sessions.get(sessionId);
        if (state != null && isExpired(state)) {
            sessions.remove(sessionId);
            return null;
        }
        return state;
    }

    public OAuthFlowState getOrCreateSession(String sessionId) {
        OAuthFlowState state = getSession(sessionId);
        if (state == null) {
            state = new OAuthFlowState();
            if (sessionId != null) {
                sessions.put(sessionId, state);
            }
        }
        return state;
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private boolean isExpired(OAuthFlowState state) {
        return Duration.between(state.getCreatedAt(), Instant.now()).compareTo(SESSION_TTL) > 0;
    }

    private void evictExpired() {
        if (sessions.size() > MAX_SESSIONS) {
            for (Map.Entry<String, OAuthFlowState> entry : sessions.entrySet()) {
                if (isExpired(entry.getValue())) {
                    sessions.remove(entry.getKey());
                }
            }
        }
    }
}

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
public class OidcSessionStore {

    private static final Duration SESSION_TTL = Duration.ofHours(1);
    private static final int MAX_SESSIONS = 1000;

    private final ConcurrentHashMap<String, OidcFlowState> sessions = new ConcurrentHashMap<>();

    public String createSession() {
        evictExpired();
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new OidcFlowState());
        return sessionId;
    }

    public OidcFlowState getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        OidcFlowState state = sessions.get(sessionId);
        if (state != null && isExpired(state)) {
            sessions.remove(sessionId);
            return null;
        }
        return state;
    }

    public OidcFlowState getOrCreateSession(String sessionId) {
        OidcFlowState state = getSession(sessionId);
        if (state == null) {
            state = new OidcFlowState();
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

    private boolean isExpired(OidcFlowState state) {
        return Duration.between(state.getCreatedAt(), Instant.now()).compareTo(SESSION_TTL) > 0;
    }

    private void evictExpired() {
        if (sessions.size() > MAX_SESSIONS) {
            for (Map.Entry<String, OidcFlowState> entry : sessions.entrySet()) {
                if (isExpired(entry.getValue())) {
                    sessions.remove(entry.getKey());
                }
            }
        }
    }
}

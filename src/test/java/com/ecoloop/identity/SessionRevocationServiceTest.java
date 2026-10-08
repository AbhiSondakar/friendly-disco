package com.ecoloop.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionRevocationServiceTest {

    private InMemorySessionRepository repository;
    private SessionRevocationService service;

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() {
        repository = new InMemorySessionRepository();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("sessionRepository", repository);
        ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> provider =
            (ObjectProvider) beans.getBeanProvider(FindByIndexNameSessionRepository.class);
        service = new SessionRevocationService(provider);
    }

    @Test
    void revokeOtherUserSessionsPreservesCurrentSession() {
        repository.sessions.put("sess-1", null);
        repository.sessions.put("sess-2", null);
        repository.sessions.put("sess-3", null);

        service.revokeOtherUserSessions("user@example.com", "sess-2");

        assertEquals(List.of("sess-1", "sess-3"), repository.deletedSessionIds);
    }

    @Test
    void revokeAllUserSessionsDeletesAllSessions() {
        repository.sessions.put("sess-1", null);
        repository.sessions.put("sess-2", null);

        service.revokeAllUserSessions("user@example.com");

        assertEquals(List.of("sess-1", "sess-2"), repository.deletedSessionIds);
    }

    private static final class InMemorySessionRepository
            implements FindByIndexNameSessionRepository<Session> {

        private final Map<String, Session> sessions = new LinkedHashMap<>();
        private final List<String> deletedSessionIds = new ArrayList<>();

        @Override
        public Map<String, Session> findByIndexNameAndIndexValue(String indexName, String indexValue) {
            return sessions;
        }

        @Override
        public Session createSession() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Session session) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Session findById(String id) {
            return sessions.get(id);
        }

        @Override
        public void deleteById(String id) {
            deletedSessionIds.add(id);
        }
    }
}

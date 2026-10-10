package com.wisekoin.backend.module.auth.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.auth.token.RefreshTokenService.RefreshSession;
import com.wisekoin.backend.module.common.exception.BadCredentialsException;
import com.wisekoin.backend.module.user.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenServiceTest {
    private RefreshTokenRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        service = new RefreshTokenService(repository);
        ReflectionTestUtils.setField(service, "refreshExpiration", 2_592_000_000L);
    }

    @Test
    void issueStoresOnlyHashOfCryptographicallyRandomToken() {
        User user = user(7L);

        String rawToken = service.issue(user);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(tokenCaptor.capture());
        RefreshToken storedToken = tokenCaptor.getValue();
        assertEquals(43, rawToken.length());
        assertEquals(64, storedToken.getTokenHash().length());
        assertNotEquals(rawToken, storedToken.getTokenHash());
        assertEquals(user, storedToken.getUser());
        assertTrue(storedToken.getExpiresAt().isAfter(storedToken.getCreatedAt()));
    }

    @Test
    void rotateInvalidatesCurrentTokenAndIssuesReplacement() {
        User user = user(7L);
        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user);
        storedToken.setExpiresAt(Instant.now().plusSeconds(60));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(storedToken));

        RefreshSession session = service.rotate("current-refresh-token");

        assertEquals(user, session.user());
        assertNotEquals("current-refresh-token", session.token());
        verify(repository).delete(storedToken);
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user(7L));
        storedToken.setExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(storedToken));

        assertThrows(BadCredentialsException.class, () -> service.rotate("expired-refresh-token"));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user@example.com");
        return user;
    }
}

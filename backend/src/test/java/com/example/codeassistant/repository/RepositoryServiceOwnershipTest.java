package com.example.codeassistant.repository;

import com.example.codeassistant.common.NotFoundException;
import com.example.codeassistant.github.GitHubService;
import com.example.codeassistant.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Verifies the core authorization guarantee from the spec: a user can only
 * ever load repositories that belong to them (see README, "Security").
 */
@ExtendWith(MockitoExtension.class)
class RepositoryServiceOwnershipTest {

    @Mock
    private RepositoryJpaRepository repositoryJpaRepository;

    @Mock
    private GitHubService gitHubService;

    @Test
    void getOwnedThrowsNotFoundWhenRepositoryBelongsToAnotherUser() {
        RepositoryService service = new RepositoryService(repositoryJpaRepository, gitHubService);
        User requestingUser = new User(1L, "alice", "alice@example.com", null);
        setId(requestingUser, 1L);

        // Simulates: repo 42 exists, but not for user 1 (it belongs to someone else)
        when(repositoryJpaRepository.findByIdAndUserId(42L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned(42L, requestingUser))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getOwnedReturnsRepositoryWhenItBelongsToTheRequestingUser() {
        RepositoryService service = new RepositoryService(repositoryJpaRepository, gitHubService);
        User requestingUser = new User(1L, "alice", "alice@example.com", null);
        setId(requestingUser, 1L);

        RepositoryEntity entity = new RepositoryEntity(
                requestingUser, 99L, "repo", "alice/repo", "alice", "main", "desc", "Java", "https://github.com/alice/repo");
        when(repositoryJpaRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(entity));

        RepositoryEntity result = service.getOwned(7L, requestingUser);

        org.assertj.core.api.Assertions.assertThat(result).isSameAs(entity);
    }

    private void setId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

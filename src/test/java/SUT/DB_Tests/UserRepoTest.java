package SUT.DB_Tests;

import com.example.meetings.MeetingsApplication;
import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link UserRepository} against a real (embedded, in-memory) H2
 * database — {@code @DataJpaTest} replaces the file-based H2 datasource configured in
 * {@code application.properties} with a throwaway embedded instance, so these tests exercise
 * real SQL/Hibernate mapping without touching the development database.
 */
@DataJpaTest
@ContextConfiguration(classes = MeetingsApplication.class)
class UserRepoTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("save - should persist the user and generate a non-blank iCal token")
    void saveGeneratesIcalToken() {
        User saved = userRepository.save(new User("testuser1", "testuser1@example.com", "hash"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIcalToken()).isNotBlank();
    }

    @Test
    @DisplayName("findByUsername - should return the user when a matching username exists")
    void findByUsernameReturnsUser() {
        userRepository.save(new User("testuser1", "testuser1@example.com", "hash"));

        Optional<User> found = userRepository.findByUsername("testuser1");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("testuser1@example.com");
    }

    @Test
    @DisplayName("findByUsername - should return empty when no user has that username")
    void findByUsernameReturnsEmptyForUnknownUser() {
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }

    @Test
    @DisplayName("existsByUsername - should become true only after a user with that username is saved")
    void existsByUsernameReflectsSavedUsers() {
        assertThat(userRepository.existsByUsername("testuser1")).isFalse();

        userRepository.save(new User("testuser1", "testuser1@example.com", "hash"));

        assertThat(userRepository.existsByUsername("testuser1")).isTrue();
    }

    @Test
    @DisplayName("findByIcalToken - should return the user that owns the given token")
    void findByIcalTokenReturnsOwner() {
        User saved = userRepository.save(new User("testuser1", "testuser1@example.com", "hash"));

        Optional<User> found = userRepository.findByIcalToken(saved.getIcalToken());

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser1");
    }

    @Test
    @DisplayName("findByIcalToken - should return empty for a token that does not exist")
    void findByIcalTokenReturnsEmptyForUnknownToken() {
        assertThat(userRepository.findByIcalToken("does-not-exist")).isEmpty();
    }

    @Test
    @DisplayName("save - should reject a second user with a username that is already taken")
    void saveRejectsDuplicateUsername() {
        userRepository.save(new User("testuser1", "testuser1@example.com", "hash"));
        userRepository.flush();

        assertThatThrownBy(() -> {
            userRepository.save(new User("testuser1", "other@example.com", "hash2"));
            userRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
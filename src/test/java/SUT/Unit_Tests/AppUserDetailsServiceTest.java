package SUT.Unit_Tests;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.AppUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppUserDetailsService Unit Tests")
class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    private String testUsername;
    private String testEmail;
    private String testPassword;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUsername = "testuser";
        testEmail = "test@example.com";
        testPassword = "$2a$10$encoded_password_hash";
        testUser = new User(testUsername, testEmail, testPassword);
    }

    @Test
    @DisplayName("loadUserByUsername - should return UserDetails when user exists")
    void testLoadUserByUsernameSuccess() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        UserDetails result = appUserDetailsService.loadUserByUsername(testUsername);

        assertNotNull(result);
        assertEquals(testUsername, result.getUsername());
        assertEquals(testPassword, result.getPassword());
        verify(userRepository).findByUsername(testUsername);
    }

    @Test
    @DisplayName("loadUserByUsername - should throw exception when user not found")
    void testLoadUserByUsernameNotFound() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
            UsernameNotFoundException.class,
            () -> appUserDetailsService.loadUserByUsername(testUsername)
        );

        assertTrue(exception.getMessage().contains("Unknown user"));
        verify(userRepository).findByUsername(testUsername);
    }

    @Test
    @DisplayName("loadUserByUsername - should include ROLE_USER authority")
    void testLoadUserByUsernameHasRoleUser() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        UserDetails result = appUserDetailsService.loadUserByUsername(testUsername);

        Collection<? extends GrantedAuthority> authorities = result.getAuthorities();
        assertNotNull(authorities);
        assertTrue(authorities.stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER")),
            "User should have ROLE_USER authority");
    }

    @Test
    @DisplayName("loadUserByUsername - should set correct password from user")
    void testLoadUserByUsernamePasswordCorrect() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        UserDetails result = appUserDetailsService.loadUserByUsername(testUsername);

        assertEquals(testPassword, result.getPassword());
    }

    @Test
    @DisplayName("loadUserByUsername - should have exception message with username")
    void testLoadUserByUsernameExceptionMessageFormat() {
        String nonExistentUser = "nonexistent";
        when(userRepository.findByUsername(nonExistentUser)).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
            UsernameNotFoundException.class,
            () -> appUserDetailsService.loadUserByUsername(nonExistentUser)
        );

        assertEquals("Unknown user: " + nonExistentUser, exception.getMessage());
    }

    @Test
    @DisplayName("loadUserByUsername - should call repository with correct username")
    void testLoadUserByUsernameCallsRepositoryWithCorrectArg() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        appUserDetailsService.loadUserByUsername(testUsername);

        verify(userRepository).findByUsername(testUsername);
        verify(userRepository, times(1)).findByUsername(testUsername);
    }

    @Test
    @DisplayName("loadUserByUsername - should return enabled user")
    void testLoadUserByUsernameIsEnabled() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        UserDetails result = appUserDetailsService.loadUserByUsername(testUsername);

        assertTrue(result.isEnabled());
    }

    @Test
    @DisplayName("loadUserByUsername - should return user with account not expired")
    void testLoadUserByUsernameAccountNotExpired() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        UserDetails result = appUserDetailsService.loadUserByUsername(testUsername);

        assertTrue(result.isAccountNonExpired());
    }

    @Test
    @DisplayName("loadUserByUsername - should return user with account not locked")
    void testLoadUserByUsernameAccountNotLocked() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        UserDetails result = appUserDetailsService.loadUserByUsername(testUsername);

        assertTrue(result.isAccountNonLocked());
    }

    @Test
    @DisplayName("loadUserByUsername - should return user with credentials not expired")
    void testLoadUserByUsernameCredentialsNotExpired() {
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        UserDetails result = appUserDetailsService.loadUserByUsername(testUsername);

        assertTrue(result.isCredentialsNonExpired());
    }

    @Test
    @DisplayName("loadUserByUsername - should handle different usernames")
    void testLoadUserByUsernameDifferentUsernames() {
        String username1 = "user1";
        String username2 = "user2";
        User user1 = new User(username1, "user1@example.com", "hash1");
        User user2 = new User(username2, "user2@example.com", "hash2");

        when(userRepository.findByUsername(username1)).thenReturn(Optional.of(user1));
        when(userRepository.findByUsername(username2)).thenReturn(Optional.of(user2));

        UserDetails result1 = appUserDetailsService.loadUserByUsername(username1);
        UserDetails result2 = appUserDetailsService.loadUserByUsername(username2);

        assertEquals(username1, result1.getUsername());
        assertEquals(username2, result2.getUsername());
        verify(userRepository).findByUsername(username1);
        verify(userRepository).findByUsername(username2);
    }
}


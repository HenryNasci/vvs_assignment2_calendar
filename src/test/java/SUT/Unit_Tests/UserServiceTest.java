package SUT.Unit_Tests;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private String testUsername;
    private String testEmail;
    private String testPassword;
    private String encodedPassword;

    @BeforeEach
    void setUp() {
        testUsername = "testuser";
        testEmail = "test@example.com";
        testPassword = "password123";
        encodedPassword = "$2a$10$encoded_password_hash";
    }

    // Line coverage: Lines 1, 3 and 4
    // Branch coverage: existsByUsername == false
    @Test
    @DisplayName("register - should successfully register a new user")
    void testRegisterSuccess() {
        // Arrange
        when(userRepository.existsByUsername(testUsername)).thenReturn(false);
        when(passwordEncoder.encode(testPassword)).thenReturn(encodedPassword);

        User expectedUser = new User(testUsername, testEmail, encodedPassword);
        when(userRepository.save(any(User.class))).thenReturn(expectedUser);

        User result = userService.register(testUsername, testEmail, testPassword);

        assertNotNull(result);
        assertEquals(testUsername, result.getUsername());
        assertEquals(testEmail, result.getEmail());
        assertEquals(encodedPassword, result.getPasswordHash());
        verify(userRepository).existsByUsername(testUsername);
        verify(passwordEncoder).encode(testPassword);
        verify(userRepository).save(any(User.class));
    }

    // Line coverage: Line 2
    // Branch coverage: existsByUsername == true
    @Test
    @DisplayName("register - should throw exception when username already exists")
    void testRegisterUsernameTaken() {
        when(userRepository.existsByUsername(testUsername)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.register(testUsername, testEmail, testPassword)
        );

        assertEquals("Username already taken", exception.getMessage());
        verify(userRepository).existsByUsername(testUsername);
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    // Confirms the password-encoding line runs before persistence.
    @Test
    @DisplayName("register - should encode password before saving")
    void testRegisterPasswordEncoding() {
        when(userRepository.existsByUsername(testUsername)).thenReturn(false);
        when(passwordEncoder.encode(testPassword)).thenReturn(encodedPassword);

        User expectedUser = new User(testUsername, testEmail, encodedPassword);
        when(userRepository.save(any(User.class))).thenReturn(expectedUser);

        userService.register(testUsername, testEmail, testPassword);

        verify(passwordEncoder).encode(testPassword);
    }

    // Line coverage: Line 1
    // Branch coverage: userRepository.findByUsername(username) == true
    @Test
    @DisplayName("requireByUsername - should return user when found")
    void testRequireByUsernameSuccess() {
        User expectedUser = new User(testUsername, testEmail, encodedPassword);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(expectedUser));

        User result = userService.requireByUsername(testUsername);

        assertNotNull(result);
        assertEquals(testUsername, result.getUsername());
        assertEquals(testEmail, result.getEmail());
        verify(userRepository).findByUsername(testUsername);
    }

    // Line coverage: Line 1
    // Branch coverage: orElseThrow
    @Test
    @DisplayName("requireByUsername - should throw exception when user not found")
    void testRequireByUsernameNotFound() {
        String nonExistentUser = "nonexistent";
        when(userRepository.findByUsername(nonExistentUser)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.requireByUsername(nonExistentUser)
        );

        assertEquals("Unknown user: " + nonExistentUser, exception.getMessage());
    }
}


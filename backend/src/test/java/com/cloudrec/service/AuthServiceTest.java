package com.cloudrec.service;

import com.cloudrec.entity.User;
import com.cloudrec.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository repo;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void registerStoresHashedPassword() {
        AuthService service = new AuthService(repo, encoder);
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        assertThat(service.register("alice@example.com", "secret123")).isEqualTo("Registered successfully");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getPassword()).isNotEqualTo("secret123");
        assertThat(encoder.matches("secret123", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        AuthService service = new AuthService(repo, encoder);
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(new User()));

        assertThat(service.register("alice@example.com", "secret123")).isEqualTo("User already exists");
        verify(repo, never()).save(any());
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        AuthService service = new AuthService(repo, encoder);
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user("alice@example.com", "secret123")));

        assertThatCode(() -> service.login("alice@example.com", "secret123")).doesNotThrowAnyException();
    }

    @Test
    void loginFailsWithWrongPassword() {
        AuthService service = new AuthService(repo, encoder);
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user("alice@example.com", "secret123")));

        assertThatThrownBy(() -> service.login("alice@example.com", "wrong")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void loginFailsForUnknownUser() {
        AuthService service = new AuthService(repo, encoder);
        when(repo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody@example.com", "secret123")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unknownUserStillPaysForAPasswordCheck() {
        PasswordEncoder spy = org.mockito.Mockito.spy(encoder);
        AuthService service = new AuthService(repo, spy);
        when(repo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody@example.com", "secret123"));

        verify(spy).matches(org.mockito.ArgumentMatchers.eq("secret123"), org.mockito.ArgumentMatchers.anyString());
    }

    private User user(String email, String rawPassword) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode(rawPassword));
        return u;
    }
}

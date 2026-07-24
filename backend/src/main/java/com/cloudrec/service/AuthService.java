package com.cloudrec.service;

import com.cloudrec.entity.User;
import com.cloudrec.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository repo;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    public String register(String email, String password) {

        if (repo.findByEmail(email).isPresent()) {
            return "User already exists";
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        repo.save(user);

        return "Registered successfully";
    }

    public void login(String email, String password) {

        User user = repo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!encoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }
    }
}

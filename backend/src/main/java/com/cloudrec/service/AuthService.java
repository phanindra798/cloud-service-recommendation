package com.cloudrec.service;

import com.cloudrec.entity.User;
import com.cloudrec.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    /** Checked against for unknown emails so login takes the same time either way. */
    private final String dummyHash;

    public AuthService(UserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("timing-equaliser");
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

        User user = repo.findByEmail(email).orElse(null);
        if (user == null) {
            // Same bcrypt cost as a real check, so response time doesn't reveal which emails exist
            encoder.matches(password, dummyHash);
            throw new RuntimeException("User not found");
        }

        if (!encoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }
    }
}

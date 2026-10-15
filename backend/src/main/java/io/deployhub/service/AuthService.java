package io.deployhub.service;

import io.deployhub.config.AuthUser;
import io.deployhub.config.JwtService;
import io.deployhub.domain.User;
import io.deployhub.dto.Dtos.AuthRequest;
import io.deployhub.dto.Dtos.AuthResponse;
import io.deployhub.repo.UserRepository;
import io.deployhub.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AuthResponse register(AuthRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = users.save(new User(email, encoder.encode(req.password())));
        return token(user);
    }

    public AuthResponse login(AuthRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
        return token(user);
    }

    private AuthResponse token(User user) {
        return new AuthResponse(jwt.issue(new AuthUser(user.getId(), user.getEmail())), user.getEmail());
    }
}

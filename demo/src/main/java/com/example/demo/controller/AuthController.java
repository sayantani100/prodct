package com.example.demo.controller;

import com.example.demo.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    public static class LoginRequest {
        public String username;
        public String password;
    }

    // *** PLACEHOLDER -- NOT SAFE TO DEPLOY AS-IS ***
    //
    // This does NOT check against a real user store. There is no password
    // hashing, no database lookup, and the credentials below are hardcoded.
    // I don't have your user/staff table or repository, so I can't wire a
    // real check without guessing at your schema.
    //
    // Before this handles real patient data, replace the body of this
    // method with:
    //   1. A lookup of `username` against your actual user repository.
    //   2. A password check using Spring Security's PasswordEncoder
    //      (e.g. BCryptPasswordEncoder) against a HASHED stored password --
    //      never compare plaintext passwords directly.
    //   3. Pulling that user's real role from your user store, not a
    //      hardcoded string.
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // TODO: replace with real lookup + BCrypt password verification.
        if ("sayantani".equals(request.username) && "6102".equals(request.password)) {
            String token = jwtUtil.generateToken(request.username, "REVIEWER");
            return ResponseEntity.ok().body(new TokenResponse(token));
        }

        return ResponseEntity.status(401).body("Invalid credentials");
    }

    public static class TokenResponse {
        public String token;
        public TokenResponse(String token) { this.token = token; }
    }
}

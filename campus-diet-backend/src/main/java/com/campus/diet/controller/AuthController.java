package com.campus.diet.controller;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.service.AuthService;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginBody body) {
        AuthService.LoginToken t = authService.login(body.getUsername(), body.getPassword());
        return ApiResponse.ok(tokenPayload(t));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterBody body) {
        AuthService.LoginToken t =
                authService.register(body.getUsername(), body.getPassword(), body.getRole());
        return ApiResponse.ok(tokenPayload(t));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    private Map<String, Object> tokenPayload(AuthService.LoginToken t) {
        Map<String, Object> m = new HashMap<>();
        m.put("token", t.token);
        Map<String, Object> user = new HashMap<>();
        user.put("id", t.user.getId());
        user.put("username", t.user.getUsername());
        user.put("role", t.user.getRole());
        m.put("user", user);
        return m;
    }

    @Data
    public static class LoginBody {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class RegisterBody {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        /** 可选：USER（默认）、CANTEEN_MANAGER */
        private String role;
    }
}

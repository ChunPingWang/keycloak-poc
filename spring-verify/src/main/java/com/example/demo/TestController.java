package com.example.demo;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/api/hello")
    public String hello(Authentication auth) {
        return "hello " + auth.getName() + " authorities=" + auth.getAuthorities();
    }

    @GetMapping("/api/admin/ping")
    public String adminPing(Authentication auth) {
        return "admin ok for " + auth.getName();
    }
}

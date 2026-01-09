/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.security.jwt;

import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing testing authentication token.
 */
@RestController
@RequestMapping("/api")
public class TestAuthenticationResource {

    /**
     * {@code GET  /authenticate} : check if the authentication token correctly validates
     *
     * @return ok.
     */
    @GetMapping("/authenticate")
    public String isAuthenticated() {
        return "ok";
    }
}

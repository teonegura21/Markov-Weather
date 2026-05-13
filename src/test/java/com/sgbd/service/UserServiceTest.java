package com.sgbd.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    @Test
    void testSha256EmptyString() {
        String hash = UserService.sha256("");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void testSha256KnownValue() {
        String hash = UserService.sha256("test");
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", hash);
    }

    @Test
    void testSha256Consistency() {
        String h1 = UserService.sha256("parola123");
        String h2 = UserService.sha256("parola123");
        assertEquals(h1, h2);
    }

    @Test
    void testSha256DifferentValues() {
        String h1 = UserService.sha256("parola123");
        String h2 = UserService.sha256("parola124");
        assertNotEquals(h1, h2);
    }

    @Test
    void testSha256Unicode() {
        String hash = UserService.sha256("ăîșțâ");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}

package com.sgbd.util;

import com.sgbd.model.City;
import com.sgbd.model.User;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @Test
    void testSingleton() {
        SessionManager s1 = SessionManager.getInstance();
        SessionManager s2 = SessionManager.getInstance();
        assertSame(s1, s2);
    }

    @Test
    void testCityChangeListener() {
        SessionManager session = SessionManager.getInstance();
        AtomicReference<City> captured = new AtomicReference<>();
        session.addCityChangeListener(captured::set);

        City city = new City();
        city.setId(99);
        city.setName("TestCity");
        session.setSelectedCity(city);

        assertNotNull(captured.get());
        assertEquals(99, captured.get().getId());
    }

    @Test
    void testUserSession() {
        SessionManager session = SessionManager.getInstance();
        assertNull(session.getCurrentUser());
        assertFalse(session.isLoggedIn());

        User user = new User();
        user.setId(5);
        user.setUsername("testuser");
        user.setReputation(0.85);

        AtomicReference<User> captured = new AtomicReference<>();
        session.addUserChangeListener(captured::set);

        session.setCurrentUser(user);
        assertTrue(session.isLoggedIn());
        assertEquals("testuser", session.getCurrentUser().getUsername());
        assertNotNull(captured.get());
        assertEquals(5, captured.get().getId());

        session.setCurrentUser(null);
        assertFalse(session.isLoggedIn());
    }
}

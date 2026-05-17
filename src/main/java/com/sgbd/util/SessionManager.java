package com.sgbd.util;

import com.sgbd.model.City;
import com.sgbd.model.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manager de sesiune singleton care păstrează starea globală a aplicației.
 * Orașul selectat și data de referință sunt centralizate aici.
 * Toate tab-urile se abonează la schimbări și se reîncarcă automat.
 */
public class SessionManager {

    private static SessionManager instance;

    private City selectedCity;
    private LocalDate selectedDate = LocalDate.now();
    private User currentUser;
    private final List<Consumer<City>> cityListeners = new ArrayList<>();
    private final List<Consumer<LocalDate>> dateListeners = new ArrayList<>();
    private final List<Runnable> refreshListeners = new ArrayList<>();
    private final List<Consumer<User>> userListeners = new ArrayList<>();

    private SessionManager() { }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public City getSelectedCity() {
        return selectedCity;
    }

    public void setSelectedCity(City city) {
        if (city == null) {
            return;
        }
        boolean changed = selectedCity == null || selectedCity.getId() != city.getId();
        this.selectedCity = city;
        if (changed) {
            AppState.getInstance().setLastSelectedCityId(city.getId());
            AppState.getInstance().save();
            notifyCityChanged(city);
        }
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(LocalDate date) {
        if (date == null) {
            return;
        }
        boolean changed = !selectedDate.equals(date);
        this.selectedDate = date;
        if (changed) {
            AppState.getInstance().setLastSelectedDate(date.toString());
            AppState.getInstance().save();
            notifyDateChanged(date);
        }
    }

    public void addCityChangeListener(Consumer<City> listener) {
        cityListeners.add(listener);
    }

    public void removeCityChangeListener(Consumer<City> listener) {
        cityListeners.remove(listener);
    }

    public void addDateChangeListener(Consumer<LocalDate> listener) {
        dateListeners.add(listener);
    }

    public void addRefreshListener(Runnable listener) {
        refreshListeners.add(listener);
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        boolean changed = (currentUser == null && user != null)
            || (currentUser != null && !currentUser.equals(user));
        this.currentUser = user;
        if (changed) {
            notifyUserChanged(user);
        }
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void addUserChangeListener(Consumer<User> listener) {
        userListeners.add(listener);
    }

    public void removeUserChangeListener(Consumer<User> listener) {
        userListeners.remove(listener);
    }

    private void notifyUserChanged(User user) {
        for (Consumer<User> c : new ArrayList<>(userListeners)) {
            try {
                c.accept(user);
            } catch (Exception ignored) { }
        }
    }

    public void refreshAll() {
        for (Runnable r : new ArrayList<>(refreshListeners)) {
            try {
                r.run();
            } catch (Exception ignored) { }
        }
    }

    private void notifyCityChanged(City city) {
        for (Consumer<City> c : new ArrayList<>(cityListeners)) {
            try {
                c.accept(city);
            } catch (Exception ignored) { }
        }
    }

    private void notifyDateChanged(LocalDate date) {
        for (Consumer<LocalDate> c : new ArrayList<>(dateListeners)) {
            try {
                c.accept(date);
            } catch (Exception ignored) { }
        }
    }
}

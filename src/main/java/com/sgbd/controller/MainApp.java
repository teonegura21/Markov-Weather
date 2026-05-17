package com.sgbd.controller;

import com.sgbd.model.City;
import com.sgbd.model.User;
import com.sgbd.service.BackgroundSyncService;
import com.sgbd.service.CityService;
import com.sgbd.service.DataPopulationService;
import com.sgbd.service.ForecastService;
import com.sgbd.service.UserService;
import com.sgbd.service.WeatherImporterService;
import com.sgbd.util.AppState;
import com.sgbd.util.DatabaseConnectionPool;
import com.sgbd.util.DatabaseInitializer;
import com.sgbd.util.SessionManager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MainApp extends Application {
    private static final Logger logger = Logger.getLogger(MainApp.class.getName());
    private final ForecastService forecastService = new ForecastService();
    private final CityService cityService = new CityService();
    private final UserService userService = new UserService();
    private final SessionManager session = SessionManager.getInstance();
    private final BackgroundSyncService syncService = new BackgroundSyncService();
    private final AppState appState = AppState.getInstance();

    private Label statusLabel;
    private VBox mainContainer;
    private StackPane contentPane;
    private HBox bottomNav;
    private HBox userArea;
    private final Map<String, Node> viewCache = new LinkedHashMap<>();
    private final Map<String, Object> controllerCache = new LinkedHashMap<>();
    private String currentViewKey;

    /** Callback static pentru navigare din controllere. */
    private static java.util.function.Consumer<String> navCallback;

    private final String[][] NAV_ITEMS = {
        {"acum", "🌤️", "Acum"},
        {"harta", "🗺️", "Hartă"},
        {"maiMulte", "⋮", "Mai multe"}
    };

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Prognoza Meteo — România");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(700);

        boolean dbReady = initDatabase();

        // Main layout: VBox with header, content, bottom nav
        mainContainer = new VBox();
        mainContainer.setStyle("-fx-background-color: #0f172a;");
        VBox.setVgrow(mainContainer, Priority.ALWAYS);

        HBox appHeader = buildAppHeader();
        contentPane = new StackPane();
        contentPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(contentPane, Priority.ALWAYS);

        bottomNav = buildBottomNav();
        HBox navWrapper = new HBox(bottomNav);
        navWrapper.setAlignment(Pos.CENTER);
        navWrapper.setPadding(new Insets(8, 16, 12, 16));
        navWrapper.setStyle("-fx-background-color: rgba(15, 23, 42, 0.95);");

        mainContainer.getChildren().addAll(appHeader, contentPane, navWrapper);

        Scene scene = new Scene(mainContainer
            , appState.getWindowWidth()
            , appState.getWindowHeight());
        scene.getStylesheets().add(
            getClass().getResource("/com/sgbd/css/style.css").toExternalForm());

        scene.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                int idx = switch (event.getCode()) {
                    case DIGIT1 -> 0;
                    case DIGIT2 -> 1;
                    case DIGIT3 -> 2;
                    default -> -1;
                };
                if (idx >= 0 && idx < NAV_ITEMS.length) {
                    switchView(NAV_ITEMS[idx][0]);
                }
            }
        });

        primaryStage.setScene(scene);
        primaryStage.show();

        navCallback = this::switchView;

        Platform.runLater(() -> loadCitiesAndRestoreSelection());
        if (dbReady) {
            checkDatabasePopulation();
            if (appState.isAutoSyncOnStartup()) {
                syncService.setStatusCallback(
                    msg -> Platform.runLater(() -> statusLabel.setText(msg)));
                syncService.runStartupSync();
            }
        }

        primaryStage.setOnCloseRequest(event -> {
            appState.setWindowWidth(primaryStage.getWidth());
            appState.setWindowHeight(primaryStage.getHeight());
            appState.save();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Aplicatia se inchide — eliberez resursele...");
            DatabaseConnectionPool.shutdown();
        }));
    }

    private boolean initDatabase() {
        boolean dbReady = DatabaseConnectionPool.initialize();
        if (!dbReady) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Eroare bază de date");
            alert.setHeaderText("Nu s-a putut conecta la PostgreSQL");
            alert.setContentText(
                "Verifică că serverul rulează și că variabilele DB_URL, "
                + "DB_USER, DB_PASSWORD sunt setate.");
            alert.showAndWait();
            return false;
        }
        return DatabaseInitializer.initialize();
    }

    private HBox buildAppHeader() {
        HBox header = new HBox(16);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 20, 10, 20));

        Label title = new Label("🌤️ Prognoză Meteo");
        title.getStyleClass().add("app-title");

        Label refreshBtn = new Label("🔄");
        refreshBtn.setStyle("-fx-font-size: 16px; -fx-cursor: hand;");
        refreshBtn.setOnMouseClicked(e -> {
            syncService.refreshNow();
            session.refreshAll();
        });

        userArea = new HBox(8);
        userArea.setAlignment(Pos.CENTER_RIGHT);
        updateUserArea();

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        statusLabel.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(title, refreshBtn, spacer, userArea, statusLabel);
        return header;
    }

    private void updateUserArea() {
        userArea.getChildren().clear();
        if (session.isLoggedIn()) {
            User u = session.getCurrentUser();
            Label userLbl = new Label("👤 " + u.getUsername());
            userLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #38bdf8;");
            Button logoutBtn = new Button("Logout");
            logoutBtn.getStyleClass().add("button secondary");
            logoutBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2px 8px;");
            logoutBtn.setOnAction(e -> {
                session.setCurrentUser(null);
                appState.setLastLoggedInUserId(-1);
                appState.save();
                updateUserArea();
            });
            userArea.getChildren().addAll(userLbl, logoutBtn);
        } else {
            Button loginBtn = new Button("Login");
            loginBtn.getStyleClass().add("button");
            loginBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2px 8px;");
            loginBtn.setOnAction(e -> showLoginDialog());
            userArea.getChildren().add(loginBtn);
        }
    }

    private void showLoginDialog() {
        javafx.scene.control.Dialog<Void> dialog
            = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Autentificare");
        dialog.setHeaderText(null);

        VBox root = new VBox(10);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #0f172a;");
        root.setPrefWidth(320);

        TextField userField = new TextField();
        userField.setPromptText("Utilizator");
        userField.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05); "
            + "-fx-text-fill: #f8fafc;");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Parolă");
        passField.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05); "
            + "-fx-text-fill: #f8fafc;");

        Label msg = new Label();
        msg.setStyle("-fx-font-size: 12px; -fx-text-fill: #ef4444;");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("button");
        loginBtn.setOnAction(e -> {
            new Thread(() -> {
                try {
                    User u = userService.login(userField.getText()
                        , passField.getText());
                    Platform.runLater(() -> {
                        if (u != null) {
                            session.setCurrentUser(u);
                            appState.setLastLoggedInUserId(u.getId());
                            appState.save();
                            updateUserArea();
                            dialog.close();
                        } else {
                            msg.setText("Utilizator sau parolă incorectă.");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> msg.setText(
                        "Eroare: " + ex.getMessage()));
                }
            }).start();
        });

        Button regBtn = new Button("Înregistrare");
        regBtn.getStyleClass().add("button secondary");
        regBtn.setOnAction(e -> {
            new Thread(() -> {
                try {
                    User u = userService.register(userField.getText()
                        , passField.getText());
                    Platform.runLater(() -> {
                        if (u != null) {
                            session.setCurrentUser(u);
                            appState.setLastLoggedInUserId(u.getId());
                            appState.save();
                            updateUserArea();
                            dialog.close();
                        } else {
                            msg.setText("Înregistrare eșuată.");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> msg.setText(
                        "Eroare: " + ex.getMessage()));
                }
            }).start();
        });

        HBox btns = new HBox(8, loginBtn, regBtn);
        btns.setAlignment(Pos.CENTER);

        root.getChildren().addAll(userField, passField, msg, btns);
        dialog.getDialogPane().setContent(root);
        dialog.show();
    }

    private void restoreLoggedInUser() {
        int uid = appState.getLastLoggedInUserId();
        if (uid > 0) {
            try {
                List<User> users = userService.getAllUsers();
                users.stream().filter(u -> u.getId() == uid).findFirst()
                    .ifPresent(session::setCurrentUser);
            } catch (Exception e) {
                logger.warning("Nu s-a putut restaura userul logat: "
                    + e.getMessage());
            }
        }
    }

    private HBox buildBottomNav() {
        HBox nav = new HBox();
        nav.getStyleClass().add("bottom-nav");
        nav.setAlignment(Pos.CENTER);
        nav.setSpacing(8);
        nav.setPadding(new Insets(6, 8, 6, 8));

        for (String[] item : NAV_ITEMS) {
            VBox btn = createNavButton(item[0], item[1], item[2]);
            nav.getChildren().add(btn);
        }

        return nav;
    }

    private VBox createNavButton(String key, String icon, String label) {
        VBox btn = new VBox(2);
        btn.setAlignment(Pos.CENTER);
        btn.setPadding(new Insets(4, 12, 4, 12));
        btn.getStyleClass().add("nav-button");
        btn.setOnMouseClicked(e -> switchView(key));

        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("nav-icon");

        Label textLbl = new Label(label);
        textLbl.setStyle("-fx-font-size: 10px;");

        btn.getChildren().addAll(iconLbl, textLbl);
        btn.setUserData(key);
        return btn;
    }

    private void switchView(String key) {
        currentViewKey = key;

        Node view = viewCache.computeIfAbsent(key, k -> {
            Object controller = createController(k);
            if (controller != null) {
                controllerCache.put(k, controller);
                try {
                    return (Node) controller.getClass().getMethod("getView")
                        .invoke(controller);
                } catch (Exception e) {
                    logger.warning("Eroare la încărcarea view-ului " + k
                        + ": " + e.getMessage());
                    return new Label("Eroare la încărcare");
                }
            }
            return new Label("View necunoscut");
        });

        contentPane.getChildren().setAll(view);
        updateNavStyles();
    }

    private Object createController(String key) {
        return switch (key) {
            case "acum" -> new UnifiedDashboardController();
            case "harta" -> new MapController();
            case "maiMulte" -> new MoreController();
            default -> null;
        };
    }

    private void updateNavStyles() {
        for (Node node : bottomNav.getChildren()) {
            if (node instanceof VBox btn) {
                String key = (String) btn.getUserData();
                if (key.equals(currentViewKey)) {
                    btn.getStyleClass().remove("nav-button");
                    btn.getStyleClass().add("nav-button-active");
                } else {
                    btn.getStyleClass().remove("nav-button-active");
                    btn.getStyleClass().add("nav-button");
                }
            }
        }
    }

    private void loadCitiesAndRestoreSelection() {
        try {
            List<City> cities = cityService.getAllCities();
            if (!cities.isEmpty()) {
                int savedId = appState.getLastSelectedCityId();
                City toSelect = cities.stream()
                    .filter(c -> c.getId() == savedId)
                    .findFirst()
                    .orElse(cities.get(0));
                session.setSelectedCity(toSelect);
            }
        } catch (SQLException e) {
            statusLabel.setText("Eroare orașe");
        }

        switchView("acum");
    }

    private void checkDatabasePopulation() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                boolean populated = DatabaseInitializer.isDatabasePopulated();
                WeatherImporterService.FreshnessStatus status
                    = forecastService.checkDataFreshness();
                if (!populated || status.totalForecastCount == 0) {
                    Platform.runLater(() -> statusLabel.setText(
                        "🔄 Import date inițiale..."));
                    new DataPopulationService().populateAll(2, 10);
                    Platform.runLater(() -> {
                        statusLabel.setText("✅ Date importate");
                        session.refreshAll();
                    });
                } else {
                    Platform.runLater(() -> statusLabel.setText(
                        "✅ " + status.totalForecastCount + " prognoze"));
                }
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText(
                    "⚠ " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Navighează la view-ul principal (Acum).
     * Poate fi apelat din controllere pentru a reveni la ecranul principal.
     */
    public static void navigateHome() {
        if (navCallback != null) {
            javafx.application.Platform.runLater(() ->
                navCallback.accept("acum"));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

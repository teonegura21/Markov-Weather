package com.sgbd.controller;

import com.sgbd.model.City;
import com.sgbd.model.Comment;
import com.sgbd.model.Forecast;
import com.sgbd.service.BackgroundSyncService;
import com.sgbd.service.CityService;
import com.sgbd.service.ForecastService;
import com.sgbd.service.StatisticsService;
import com.sgbd.service.UserService;
import com.sgbd.util.AppState;
import com.sgbd.util.BaseController;
import com.sgbd.util.SessionManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tab "Mai multe" — Comentarii, voturi, clasamente mini, setări.
 */
public class MoreController extends BaseController {

    private final CityService cityService = new CityService();
    private final ForecastService forecastService = new ForecastService();
    private final UserService userService = new UserService();
    private final StatisticsService statsService = new StatisticsService();
    private final SessionManager session = SessionManager.getInstance();
    private final AppState appState = AppState.getInstance();
    private final BackgroundSyncService syncService = new BackgroundSyncService();
    private Label celsiusStatus;
    private Label windStatus;
    private Label syncStatus;

    @Override
    protected void buildContent(VBox container) {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        // Card Setări
        VBox settingsCard = createCard("⚙️ Setări");

        HBox celsiusRow = createSettingRow(
            "Unitate temperatură"
            , appState.isCelsiusUnit() ? "Celsius" : "Fahrenheit"
            , () -> {
                appState.setCelsiusUnit(!appState.isCelsiusUnit());
                appState.save();
                updateSettingsLabels();
                session.refreshAll();
            });
        celsiusStatus = (Label) celsiusRow.getChildren().get(1);

        HBox windRow = createSettingRow("Unitate vânt", appState.getWindSpeedUnit(), () -> {
            String next = switch (appState.getWindSpeedUnit()) {
                case "km/h" -> "m/s";
                case "m/s" -> "mph";
                default -> "km/h";
            };
            appState.setWindSpeedUnit(next);
            appState.save();
            updateSettingsLabels();
            session.refreshAll();
        });
        windStatus = (Label) windRow.getChildren().get(1);

        HBox syncRow = createSettingRow(
            "Sincronizare la pornire"
            , appState.isAutoSyncOnStartup() ? "Activată" : "Dezactivată"
            , () -> {
                appState.setAutoSyncOnStartup(!appState.isAutoSyncOnStartup());
            appState.save();
            updateSettingsLabels();
        });
        syncStatus = (Label) syncRow.getChildren().get(1);

        Button recomputeBtn = new Button("🔄 Rulează ML complet");
        recomputeBtn.getStyleClass().add("button");
        recomputeBtn.setOnAction(e -> syncService.refreshNow());

        settingsCard.getChildren().addAll(celsiusRow, windRow, syncRow, recomputeBtn);

        // Card Comunitate
        VBox communityCard = createCard("💬 Comunitate");
        Label commDesc = new Label("Autentifică-te pentru a vota acuratețea prognozelor și a adăuga comentarii.");
        commDesc.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8; -fx-wrap-text: true;");
        commDesc.setMaxWidth(400);
        Button openComments = new Button("Deschide comentarii");
        openComments.getStyleClass().add("button");
        openComments.setOnAction(e -> showCommentsDialog());
        communityCard.getChildren().addAll(commDesc, openComments);

        // Card Despre
        VBox aboutCard = createCard("ℹ️ Despre");
        Label about = new Label("Prognoză Meteo România\nDate furnizate de Open-Meteo API\nVersiunea 1.0.0");
        about.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
        aboutCard.getChildren().add(about);

        content.getChildren().addAll(settingsCard, communityCard, aboutCard);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        container.getChildren().add(scroll);
    }

    private HBox createSettingRow(String label, String value, Runnable onToggle) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 8, 0));

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #e2e8f0;");
        HBox.setHgrow(lbl, Priority.ALWAYS);

        Label val = new Label(value);
        val.setStyle("-fx-font-size: 13px; -fx-text-fill: #38bdf8; -fx-font-weight: bold;");

        Button toggle = new Button("Schimbă");
        toggle.getStyleClass().add("button secondary");
        toggle.setOnAction(e -> onToggle.run());

        row.getChildren().addAll(lbl, val, toggle);
        return row;
    }

    private void updateSettingsLabels() {
        if (celsiusStatus != null) {
            celsiusStatus.setText(appState.isCelsiusUnit() ? "Celsius" : "Fahrenheit");
        }
        if (windStatus != null) {
            windStatus.setText(appState.getWindSpeedUnit());
        }
        if (syncStatus != null) {
            syncStatus.setText(appState.isAutoSyncOnStartup() ? "Activată" : "Dezactivată");
        }
    }

    private void showCommentsDialog() {
        City city = session.getSelectedCity();
        if (city == null) {
            showAlert("Selectează un oraș mai întâi.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("💬 Comentarii și voturi");
        dialog.setHeaderText(null);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #0f172a;");
        root.setPrefWidth(520);
        root.setPrefHeight(600);

        // Forecast info
        Label forecastInfo = new Label();
        forecastInfo.setStyle("-fx-font-size: 14px; -fx-text-fill: #f8fafc; -fx-font-weight: bold;");
        root.getChildren().add(forecastInfo);

        // Vote section
        HBox voteBox = new HBox(10);
        voteBox.setAlignment(Pos.CENTER_LEFT);
        Label voteLabel = new Label("Acuratețe prognoză:");
        voteLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
        Button voteUp = new Button("✅ Corectă");
        Button voteDown = new Button("❌ Eronată");
        voteUp.getStyleClass().add("button");
        voteDown.getStyleClass().add("button secondary");
        voteBox.getChildren().addAll(voteLabel, voteUp, voteDown);
        root.getChildren().add(voteBox);

        // Comments list
        Label commentsTitle = new Label("Comentarii");
        commentsTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");
        root.getChildren().add(commentsTitle);

        VBox commentsList = new VBox(8);
        commentsList.setStyle("-fx-background-color: transparent;");
        ScrollPane commentsScroll = new ScrollPane(commentsList);
        commentsScroll.setFitToWidth(true);
        commentsScroll.setPrefHeight(280);
        commentsScroll.setStyle("-fx-background: transparent;");
        root.getChildren().add(commentsScroll);

        // Add comment section
        TextArea commentInput = new TextArea();
        commentInput.setPromptText("Scrie un comentariu...");
        commentInput.setPrefRowCount(2);
        commentInput.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05);"
            + " -fx-text-fill: #f8fafc; -fx-prompt-text-fill: #64748b;");
        Button addCommentBtn = new Button("➕ Adaugă comentariu");
        addCommentBtn.getStyleClass().add("button");
        root.getChildren().addAll(commentInput, addCommentBtn);

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getScene().getWindow().setOnCloseRequest(ev -> dialog.close());

        // Load data
        int[] forecastIdHolder = new int[1];
        com.sgbd.model.User currentUser = session.getCurrentUser();
        boolean isLoggedIn = currentUser != null;

        Label headerInfo = new Label();
        headerInfo.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
        root.getChildren().add(1, headerInfo);

        if (!isLoggedIn) {
            Label loginWarn = new Label("⚠ Autentifică-te pentru a vota și comenta.");
            loginWarn.setStyle("-fx-font-size: 13px; -fx-text-fill: #f59e0b;");
            root.getChildren().add(2, loginWarn);
            voteUp.setDisable(true);
            voteDown.setDisable(true);
            addCommentBtn.setDisable(true);
            commentInput.setDisable(true);
        } else {
            headerInfo.setText("Utilizator: " + currentUser.getUsername()
                + " | Reputație: " + String.format("%.2f", currentUser.getReputation()));
        }

        new Thread(() -> {
            try {
                Forecast report = forecastService.getDailyReport(city.getId(), session.getSelectedDate());
                if (report != null) {
                    forecastIdHolder[0] = report.getId();
                }
                Platform.runLater(() -> {
                    if (report != null) {
                        forecastInfo.setText(String.format(
                            "%s, %s — %.0f° / %.0f°"
                            , city.getName()
                            , report.getDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                            , report.getTempMin()
                            , report.getTempMax()));
                    } else {
                        forecastInfo.setText(city.getName() + " — fără prognoză");
                    }
                });
                if (forecastIdHolder[0] > 0 && isLoggedIn) {
                    double score = statsService.getForecastScore(forecastIdHolder[0]);
                    Platform.runLater(() -> {
                        headerInfo.setText(headerInfo.getText()
                            + " | Scor prognoză: " + String.format("%.2f", score));
                    });
                }
                refreshCommentsList(commentsList, forecastIdHolder[0]);
            } catch (SQLException ex) {
                Platform.runLater(() -> showAlert("Eroare: " + ex.getMessage()));
            }
        }).start();

        voteUp.setOnAction(e -> {
            if (forecastIdHolder[0] > 0 && isLoggedIn) {
                new Thread(() -> {
                    try {
                        userService.addVote(currentUser.getId(), forecastIdHolder[0], true);
                        Platform.runLater(() -> showAlert("Vot înregistrat: corectă"));
                    } catch (SQLException ex) {
                        Platform.runLater(() -> showAlert("Eroare vot: " + ex.getMessage()));
                    }
                }).start();
            }
        });

        voteDown.setOnAction(e -> {
            if (forecastIdHolder[0] > 0 && isLoggedIn) {
                new Thread(() -> {
                    try {
                        userService.addVote(currentUser.getId(), forecastIdHolder[0], false);
                        Platform.runLater(() -> showAlert("Vot înregistrat: eronată"));
                    } catch (SQLException ex) {
                        Platform.runLater(() -> showAlert("Eroare vot: " + ex.getMessage()));
                    }
                }).start();
            }
        });

        addCommentBtn.setOnAction(e -> {
            String text = commentInput.getText().trim();
            if (text.isEmpty() || forecastIdHolder[0] == 0 || !isLoggedIn) {
                return;
            }
            new Thread(() -> {
                try {
                    userService.addComment(currentUser.getId(), forecastIdHolder[0], text);
                    Platform.runLater(() -> {
                        commentInput.clear();
                        refreshCommentsList(commentsList, forecastIdHolder[0]);
                    });
                } catch (SQLException ex) {
                    Platform.runLater(() -> showAlert("Eroare comentariu: " + ex.getMessage()));
                }
            }).start();
        });

        dialog.show();
    }

    private void refreshCommentsList(VBox commentsList, int forecastId) {
        commentsList.getChildren().clear();
        if (forecastId == 0) {
            commentsList.getChildren().add(new Label("Nu există prognoză selectată"));
            return;
        }
        new Thread(() -> {
            try {
                List<Comment> comments = userService.getComments(forecastId);
                Platform.runLater(() -> {
                    if (comments.isEmpty()) {
                        Label empty = new Label("Nu există comentarii încă. Fii primul!");
                        empty.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
                        commentsList.getChildren().add(empty);
                        return;
                    }
                    for (Comment c : comments) {
                        VBox card = new VBox(4);
                        card.getStyleClass().add("glass-card");
                        card.setPadding(new Insets(10));

                        HBox top = new HBox(8);
                        top.setAlignment(Pos.CENTER_LEFT);
                        Label user = new Label(c.getUsername() != null ? c.getUsername() : "Anonim");
                        user.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #38bdf8;");
                        Label date = new Label(c.getCreatedAt().format(DateTimeFormatter.ofPattern("d MMM HH:mm")));
                        date.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
                        HBox.setHgrow(date, Priority.ALWAYS);
                        date.setAlignment(Pos.CENTER_RIGHT);
                        top.getChildren().addAll(user, date);

                        Label text = new Label(c.getCommentText());
                        text.setStyle("-fx-font-size: 13px; -fx-text-fill: #e2e8f0; -fx-wrap-text: true;");
                        text.setMaxWidth(460);

                        card.getChildren().addAll(top, text);
                        commentsList.getChildren().add(card);
                    }
                });
            } catch (SQLException ex) {
                Platform.runLater(() -> {
                    Label err = new Label("Eroare la încărcarea comentariilor");
                    err.setStyle("-fx-text-fill: #ef4444;");
                    commentsList.getChildren().add(err);
                });
            }
        }).start();
    }

    private void showAlert(String msg) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private VBox createCard(String title) {
        VBox card = new VBox(10);
        card.getStyleClass().add("glass-panel");
        card.setPadding(new Insets(16));
        card.setMaxWidth(500);
        card.setAlignment(Pos.TOP_LEFT);

        Label lbl = new Label(title);
        lbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");
        card.getChildren().add(lbl);
        return card;
    }
}

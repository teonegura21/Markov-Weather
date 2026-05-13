package com.sgbd.controller;

import com.sgbd.model.City;
import com.sgbd.model.Comment;
import com.sgbd.model.Forecast;
import com.sgbd.model.User;
import com.sgbd.service.CityService;
import com.sgbd.service.ForecastService;
import com.sgbd.service.UserService;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class CommentsController {
    private final CityService cityService = new CityService();
    private final ForecastService forecastService = new ForecastService();
    private final UserService userService = new UserService();

    private User currentUser;
    private ComboBox<City> cityCombo;
    private DatePicker datePicker;

    private TextField usernameField;
    private PasswordField passwordField;
    private Button loginBtn;
    private Button registerBtn;
    private Label userLabel;

    private TextArea commentArea;
    private ListView<String> commentsList;
    private Forecast currentForecast;

    private ToggleGroup voteGroup;
    private RadioButton accurateBtn;
    private RadioButton inaccurateBtn;

    public Node getView() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        HBox loginRow = new HBox(10);
        usernameField = new TextField();
        usernameField.setPromptText("Utilizator");
        passwordField = new PasswordField();
        passwordField.setPromptText("Parolă");
        loginBtn = new Button("Autentificare");
        registerBtn = new Button("Înregistrare");
        userLabel = new Label("Neautentificat");
        loginRow.getChildren().addAll(
            new Label("User:"), usernameField, new Label("Parolă:"), passwordField,
            loginBtn, registerBtn, userLabel);

        HBox forecastRow = new HBox(10);
        cityCombo = new ComboBox<>();
        datePicker = new DatePicker(LocalDate.now());
        Button loadBtn = new Button("Încarcă prognoza");

        forecastRow.getChildren().addAll(
            new Label("Oraș:"), cityCombo,
            new Label("Dată:"), datePicker,
            loadBtn);

        HBox voteRow = new HBox(15);
        voteGroup = new ToggleGroup();
        accurateBtn = new RadioButton("Prognoză corectă");
        accurateBtn.setToggleGroup(voteGroup);
        inaccurateBtn = new RadioButton("Prognoză incorectă");
        inaccurateBtn.setToggleGroup(voteGroup);
        Button voteBtn = new Button("Votează");
        voteRow.getChildren().addAll(new Label("Vot:"), accurateBtn, inaccurateBtn, voteBtn);

        commentArea = new TextArea();
        commentArea.setPromptText("Scrie un comentariu...");
        commentArea.setPrefRowCount(3);
        Button commentBtn = new Button("Adaugă comentariu");

        commentsList = new ListView<>();
        Button refreshBtn = new Button("Actualizează comentarii");

        root.getChildren().addAll(loginRow, new Separator(), forecastRow,
                                 voteRow, commentArea, commentBtn,
                                 refreshBtn, commentsList);

        loadCities();
        loadBtn.setOnAction(e -> loadCurrentForecast());

        loginBtn.setOnAction(e -> login());
        registerBtn.setOnAction(e -> register());
        voteBtn.setOnAction(e -> vote());
        commentBtn.setOnAction(e -> addComment());
        refreshBtn.setOnAction(e -> refreshComments());

        return root;
    }

    private void loadCities() {
        try {
            cityCombo.setItems(FXCollections.observableArrayList(cityService.getAllCities()));
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    private void login() {
        try {
            currentUser = userService.login(usernameField.getText(), passwordField.getText());
            if (currentUser != null) {
                userLabel.setText("Autentificat: " + currentUser.getUsername());
            } else {
                userLabel.setText("Autentificare eșuată");
            }
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    private void register() {
        try {
            currentUser = userService.register(usernameField.getText(), passwordField.getText());
            if (currentUser != null) {
                userLabel.setText("Înregistrat: " + currentUser.getUsername());
            } else {
                userLabel.setText("Înregistrare eșuată");
            }
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    private void loadCurrentForecast() {
        City city = cityCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (city == null || date == null) return;
        try {
            currentForecast = forecastService.getDailyReport(city.getId(), date);
            if (currentForecast == null) {
                showError("Nicio prognoză găsită pentru această dată");
            }
            refreshComments();
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    private void vote() {
        if (currentUser == null) { showError("Autentifică-te mai întâi!"); return; }
        if (currentForecast == null) { showError("Încarcă o prognoză mai întâi!"); return; }
        if (voteGroup.getSelectedToggle() == null) { showError("Selectează un vot!"); return; }

        boolean isAccurate = accurateBtn.isSelected();
        try {
            userService.addVote(currentUser.getId(), currentForecast.getId(), isAccurate);
            showInfo("Vot înregistrat!");
            refreshComments();
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    private void addComment() {
        if (currentUser == null) { showError("Autentifică-te mai întâi!"); return; }
        if (currentForecast == null) { showError("Încarcă o prognoză mai întâi!"); return; }
        String text = commentArea.getText().trim();
        if (text.isEmpty()) { showError("Scrie un comentariu!"); return; }

        try {
            userService.addComment(currentUser.getId(), currentForecast.getId(), text);
            commentArea.clear();
            refreshComments();
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    private void refreshComments() {
        if (currentForecast == null) return;
        try {
            List<Comment> comments = userService.getComments(currentForecast.getId());
            commentsList.setItems(FXCollections.observableArrayList(
                comments.stream().map(c -> c.getUsername() + ": " + c.getCommentText()).toList()
            ));
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}

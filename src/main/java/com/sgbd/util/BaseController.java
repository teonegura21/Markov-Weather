package com.sgbd.util;

import com.sgbd.model.City;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/**
 * Clasă abstractă de bază pentru toate controllerele de tab.
 * Oferă integrare cu SessionManager pentru schimbări globale de oraș/dată.
 */
public abstract class BaseController {

    protected StackPane rootContainer;
    private VBox contentBox;
    private ProgressIndicator loadingIndicator;
    private Label errorLabel;
    private boolean initialized = false;

    /**
     * Construiește și returnează view-ul acestui tab.
     * Apelat o singură dată de MainApp.
     */
    public final Node getView() {
        if (rootContainer != null) {
            return rootContainer;
        }

        rootContainer = new StackPane();
        rootContainer.getStyleClass().add("tab-root");

        contentBox = new VBox(0);
        contentBox.getStyleClass().add("tab-content");

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(32, 32);
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        loadingIndicator.getStyleClass().add("tab-loading");

        errorLabel = new Label();
        errorLabel.getStyleClass().add("tab-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        rootContainer.getChildren().addAll(contentBox, loadingIndicator, errorLabel);

        // Construiește conținutul specific
        buildContent(contentBox);

        // Abonare la schimbări globale
        SessionManager.getInstance().addCityChangeListener(this::onCityChangedInternal);
        SessionManager.getInstance().addDateChangeListener(this::onDateChangedInternal);

        return rootContainer;
    }

    private void onCityChangedInternal(City city) {
        if (city == null) return;
        Platform.runLater(() -> {
            onCityChanged(city);
        });
    }

    private void onDateChangedInternal(LocalDate date) {
        Platform.runLater(() -> {
            onDateChanged(date);
        });
    }

    /**
     * Construiește conținutul UI al tab-ului.
     * Se apelează o singură dată.
     */
    protected abstract void buildContent(VBox container);

    /**
     * Apelat când se schimbă orașul global.
     * Override opțional.
     */
    protected void onCityChanged(City city) {}

    /**
     * Apelat când se schimbă data globală.
     * Override opțional.
     */
    protected void onDateChanged(LocalDate date) {}

    protected void showLoading(boolean show) {
        Platform.runLater(() -> {
            loadingIndicator.setVisible(show);
            loadingIndicator.setManaged(show);
        });
    }

    protected void showError(String message) {
        Platform.runLater(() -> {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        });
    }

    protected void hideError() {
        Platform.runLater(() -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });
    }

    protected boolean isInitialized() {
        return initialized;
    }

    protected void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}

package com.sgbd.util;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Utilitar pentru animatii reutilizabile in interfata JavaFX.
 * Ofera tranzitii fluide pentru noduri si efecte vizuale.
 */
public final class AnimationUtil {

    private AnimationUtil() {}

    /**
     * Aplica o animatie de fade-in pe un nod.
     *
     * @param node   nodul de animat
     * @param millis durata in milisecunde
     */
    public static void fadeIn(Node node, double millis) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(millis), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    /**
     * Aplica o animatie de slide-up + fade-in.
     *
     * @param node   nodul de animat
     * @param millis durata in milisecunde
     */
    public static void slideUp(Node node, double millis) {
        node.setOpacity(0);
        node.setTranslateY(20);

        TranslateTransition tt = new TranslateTransition(Duration.millis(millis), node);
        tt.setFromY(20);
        tt.setToY(0);

        FadeTransition ft = new FadeTransition(Duration.millis(millis), node);
        ft.setFromValue(0);
        ft.setToValue(1);

        ParallelTransition pt = new ParallelTransition(tt, ft);
        pt.play();
    }

    /**
     * Creeaza o animatie de pulsare continua (glow).
     *
     * @param node   nodul de animat
     * @param millis durata ciclului in milisecunde
     * @return animatia creata (pentru a putea fi oprită)
     */
    public static Animation pulseGlow(Node node, double millis) {
        ScaleTransition st = new ScaleTransition(Duration.millis(millis), node);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(1.08);
        st.setToY(1.08);
        st.setAutoReverse(true);
        st.setCycleCount(Animation.INDEFINITE);
        st.play();
        return st;
    }

    /**
     * Creeaza o animatie de rotatie continua.
     *
     * @param node   nodul de animat
     * @param millis durata unei rotatii complete
     * @return animatia creata
     */
    public static Animation rotateContinuous(Node node, double millis) {
        RotateTransition rt = new RotateTransition(Duration.millis(millis), node);
        rt.setByAngle(360);
        rt.setCycleCount(Animation.INDEFINITE);
        rt.setInterpolator(Interpolator.LINEAR);
        rt.play();
        return rt;
    }

    /**
     * Animeaza schimbarea culorii de fundal a unui nod.
     *
     * @param node       nodul de animat
     * @param from       culoarea initiala
     * @param to         culoarea finala
     * @param millis     durata in milisecunde
     */
    public static void colorTransition(Node node, Color from, Color to, double millis) {
        Timeline timeline = new Timeline();
        KeyValue kv = new KeyValue(node.styleProperty(),
            "-fx-background-color: " + ColorUtil.toCss(to) + ";");
        KeyFrame kf = new KeyFrame(Duration.millis(millis), kv);
        timeline.getKeyFrames().add(kf);
        timeline.play();
    }

    /**
     * Animeaza numaratoarea de la 0 la o valoare tinta (pentru procente).
     *
     * @param node      Label sau Text unde se afiseaza valoarea
     * @param target    valoarea finala (0-100)
     * @param millis    durata animatiei
     */
    public static void countUp(javafx.scene.control.Label node, double target, double millis) {
        Timeline timeline = new Timeline();
        KeyValue kv = new KeyValue(new javafx.beans.property.SimpleDoubleProperty(0), target);
        KeyFrame kf = new KeyFrame(Duration.millis(millis), kv);
        timeline.getKeyFrames().add(kf);
        timeline.currentTimeProperty().addListener((obs, old, val) -> {
            double progress = val.toMillis() / millis;
            node.setText(String.format("%.0f%%", target * progress));
        });
        timeline.play();
    }

    /**
     * Aplica o animatie de bounce (scalare cu efect elastic).
     *
     * @param node   nodul de animat
     * @param millis durata in milisecunde
     */
    public static void bounce(Node node, double millis) {
        ScaleTransition st = new ScaleTransition(Duration.millis(millis), node);
        st.setFromX(0.8);
        st.setFromY(0.8);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);
        st.play();
    }

    /**
     * Aplica un efect de shake (vibratie) pe un nod.
     *
     * @param node   nodul de animat
     * @param millis durata in milisecunde
     */
    public static void shake(Node node, double millis) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(millis / 6), node);
        tt.setFromX(0);
        tt.setByX(6);
        tt.setAutoReverse(true);
        tt.setCycleCount(6);
        tt.play();
    }
}

package com.sgbd.controller;

import com.sgbd.service.MapService;
import com.sgbd.service.MapService.MapData;
import com.sgbd.util.ColorUtil;
import com.sgbd.util.EuropeMapData;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Controler pentru harta interactivă a Europei cu efecte 2.5D și animații meteo.
 * Arhitectura pe două straturi (static + dinamic) asigură performanță optimă.
 */
public class MapController {

    private static final double CANVAS_W = 1200;
    private static final double CANVAS_H = 800;
    private static final double MAP_PAD = 40;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MapService mapService = new MapService();
    private final Random rand = new Random();

    private DatePicker datePicker;
    private Canvas staticCanvas;
    private Canvas dynamicCanvas;
    private GraphicsContext staticGc;
    private GraphicsContext dynamicGc;
    private StackPane canvasLayers;

    private List<MapData> currentMapData = new ArrayList<>();
    private final List<DriftingCloud> clouds = new ArrayList<>();
    private final Map<String, CityAnimState> cityAnimStates = new HashMap<>();

    private AnimationTimer animator;
    private Timeline refreshTimeline;
    private Label lastUpdateLabel;

    private double sunRotationAngle = 0;
    private double mapScale = 1.0;

    /* ============================================================
       Clase interne pentru particule și stări de animație
       ============================================================ */

    private static class DriftingCloud {
        double x, y, w, h, speed;
        DriftingCloud(double x, double y, double w, double h, double speed) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.speed = speed;
        }
    }

    private static class RainDrop {
        double relX, y, speed;
        final double baseX, baseY;
        RainDrop(double baseX, double baseY, Random rand) {
            this.baseX = baseX; this.baseY = baseY; reset(rand);
        }
        void reset(Random rand) {
            this.relX = (rand.nextDouble() - 0.5) * 18;
            this.y = baseY - 28 - rand.nextDouble() * 12;
            this.speed = 3 + rand.nextDouble() * 3;
        }
        void update(Random rand) {
            y += speed;
            if (y > baseY + 18) reset(rand);
        }
    }

    private static class SnowFlake {
        double relX, y, speedY, phase;
        final double baseX, baseY;
        SnowFlake(double baseX, double baseY, Random rand) {
            this.baseX = baseX; this.baseY = baseY; reset(rand);
        }
        void reset(Random rand) {
            this.relX = (rand.nextDouble() - 0.5) * 20;
            this.y = baseY - 32 - rand.nextDouble() * 15;
            this.speedY = 0.4 + rand.nextDouble() * 0.8;
            this.phase = rand.nextDouble() * Math.PI * 2;
        }
        void update(Random rand) {
            y += speedY;
            phase += 0.025;
            if (y > baseY + 18) reset(rand);
        }
        double getX() { return baseX + relX + Math.sin(phase) * 5; }
    }

    private static class StormState {
        long lastFlashNs = 0;
        double flashOpacity = 0;
        boolean activeFlash = false;
        double glowPhase = Math.random() * Math.PI * 2;
    }

    private static class CityAnimState {
        final List<RainDrop> rainDrops = new ArrayList<>();
        final List<SnowFlake> snowFlakes = new ArrayList<>();
        final StormState storm = new StormState();

        CityAnimState(double x, double y, String pictograma, Random rand) {
            if (pictograma == null) return;
            String p = pictograma.toLowerCase();
            if (p.contains("ploaie") || p.contains("rain")) {
                for (int i = 0; i < 10; i++) rainDrops.add(new RainDrop(x, y, rand));
            } else if (p.contains("ninsoare") || p.contains("snow")) {
                for (int i = 0; i < 10; i++) snowFlakes.add(new SnowFlake(x, y, rand));
            }
        }
    }

    /* ============================================================
       Construcția interfeței
       ============================================================ */

    public Node getView() {
        VBox root = new VBox(0);
        root.setPadding(new Insets(0));
        root.setStyle("-fx-background-color: #0f172a;");

        // Bară de sus cu selector de dată
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 16, 12, 16));
        topBar.setStyle("-fx-background-color: rgba(15,23,42,0.95); -fx-border-color: transparent transparent rgba(148,163,184,0.1) transparent; -fx-border-width: 0 0 1px 0;");

        Label lblData = new Label("Dată:");
        lblData.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold;");
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(140);
        Button loadBtn = new Button("Afișează hartă");
        loadBtn.setOnAction(e -> loadData());

        topBar.getChildren().addAll(lblData, datePicker, loadBtn);

        // Straturi canvas: static (harta + orașe) și dinamic (particule)
        staticCanvas = new Canvas(CANVAS_W, CANVAS_H);
        dynamicCanvas = new Canvas(CANVAS_W, CANVAS_H);
        staticGc = staticCanvas.getGraphicsContext2D();
        dynamicGc = dynamicCanvas.getGraphicsContext2D();

        canvasLayers = new StackPane(staticCanvas, dynamicCanvas);

        // Group pentru ca ScrollPane să vadă dimensiunile scalate
        Group mapGroup = new Group(canvasLayers);

        ScrollPane scrollPane = new ScrollPane(mapGroup);
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #0f172a;");

        // Container principal cu legendă și status
        StackPane canvasContainer = new StackPane();
        canvasContainer.setPrefSize(CANVAS_W, CANVAS_H);
        canvasContainer.setMinSize(CANVAS_W, CANVAS_H);
        canvasContainer.getChildren().add(scrollPane);

        VBox legend = buildLegend();
        StackPane.setAlignment(legend, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(legend, new Insets(0, 16, 16, 0));
        canvasContainer.getChildren().add(legend);

        lastUpdateLabel = new Label("Ultima actualizare: --:--:--");
        lastUpdateLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        StackPane.setAlignment(lastUpdateLabel, Pos.BOTTOM_LEFT);
        StackPane.setMargin(lastUpdateLabel, new Insets(0, 0, 16, 16));
        canvasContainer.getChildren().add(lastUpdateLabel);

        // Butoane zoom în bara de sus
        Button zoomInBtn = new Button("+");
        Button zoomOutBtn = new Button("-");
        String zoomBtnStyle = "-fx-background-color: rgba(30,41,59,0.9); -fx-text-fill: #e2e8f0; -fx-font-weight: bold; -fx-font-size: 14px; -fx-min-width: 32px; -fx-min-height: 28px; -fx-cursor: hand;";
        zoomInBtn.setStyle(zoomBtnStyle);
        zoomOutBtn.setStyle(zoomBtnStyle);

        zoomInBtn.setOnAction(e -> adjustZoom(1.2));
        zoomOutBtn.setOnAction(e -> adjustZoom(1.0 / 1.2));

        HBox zoomBox = new HBox(6, zoomOutBtn, zoomInBtn);
        zoomBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(zoomBox, Priority.ALWAYS);
        topBar.getChildren().add(zoomBox);

        root.getChildren().addAll(topBar, canvasContainer);

        initParticles();
        startAutoRefresh();
        loadData();

        return root;
    }

    private void adjustZoom(double factor) {
        mapScale *= factor;
        if (mapScale < 0.5) mapScale = 0.5;
        if (mapScale > 3.0) mapScale = 3.0;
        canvasLayers.setScaleX(mapScale);
        canvasLayers.setScaleY(mapScale);
    }

    private void initParticles() {
        // Nori globali care traversează lent harta
        clouds.add(new DriftingCloud(80, 110, 150, 55, 0.22));
        clouds.add(new DriftingCloud(380, 75, 190, 65, 0.16));
        clouds.add(new DriftingCloud(720, 150, 170, 60, 0.20));
        clouds.add(new DriftingCloud(920, 95, 130, 50, 0.28));
    }

    /* ============================================================
       Încărcare date și strat static
       ============================================================ */

    private void loadData() {
        LocalDate date = datePicker.getValue();
        if (date == null) return;

        try {
            currentMapData = mapService.getMapData(null, date);
            cityAnimStates.clear();
            for (MapData md : currentMapData) {
                Point2D p = EuropeMapData.geoToCanvas(
                    md.getLatitudine(), md.getLongitudine(), CANVAS_W, CANVAS_H, MAP_PAD);
                cityAnimStates.put(md.getOras(), new CityAnimState(p.getX(), p.getY(), md.getPictograma(), rand));
            }
            drawStaticLayer();
            lastUpdateLabel.setText("Ultima actualizare: " + LocalDateTime.now().format(TIME_FMT));
        } catch (SQLException e) {
            showError("Eroare la încărcarea datelor hărții: " + e.getMessage());
        }
    }

    private void drawStaticLayer() {
        // Fundal întunecat
        staticGc.setFill(Color.web("#0f172a"));
        staticGc.fillRect(0, 0, CANVAS_W, CANVAS_H);

        drawEuropePolygons(staticGc);
        drawTerrainHints(staticGc);

        // Desenează marcatorii statici ai orașelor
        for (MapData md : currentMapData) {
            Point2D p = EuropeMapData.geoToCanvas(
                md.getLatitudine(), md.getLongitudine(), CANVAS_W, CANVAS_H, MAP_PAD);
            drawCityStatic(staticGc, md, p.getX(), p.getY());
        }

        // Pornește animația după primul desen static
        if (animator == null) {
            animator = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    drawDynamicLayer(now);
                }
            };
            animator.start();
        }
    }

    /* ============================================================
       Desenare poligoane Europa și indicații teren
       ============================================================ */

    private void drawEuropePolygons(GraphicsContext gc) {
        // Agregăm temperatura medie per țară pentru pseudo-relief
        Map<String, Double> tempSum = new HashMap<>();
        Map<String, Integer> tempCount = new HashMap<>();
        for (MapData md : currentMapData) {
            String tara = md.getTara();
            if (tara == null) continue;
            tempSum.merge(tara, md.getTempMax(), Double::sum);
            tempCount.merge(tara, 1, Integer::sum);
        }
        Map<String, Double> countryTemp = new HashMap<>();
        for (String tara : tempSum.keySet()) {
            countryTemp.put(tara, tempSum.get(tara) / tempCount.get(tara));
        }

        for (java.util.Map.Entry<String, double[][]> entry : EuropeMapData.getCountryPolygons().entrySet()) {
            String country = entry.getKey();
            double[][] poly = entry.getValue();
            if (poly == null || poly.length == 0) continue;

            gc.beginPath();
            Point2D first = EuropeMapData.geoToCanvas(poly[0][1], poly[0][0], CANVAS_W, CANVAS_H, MAP_PAD);
            gc.moveTo(first.getX(), first.getY());
            for (int i = 1; i < poly.length; i++) {
                Point2D pt = EuropeMapData.geoToCanvas(poly[i][1], poly[i][0], CANVAS_W, CANVAS_H, MAP_PAD);
                gc.lineTo(pt.getX(), pt.getY());
            }
            gc.closePath();

            Double avgTemp = countryTemp.get(country);
            if (avgTemp != null) {
                Color baseColor = ColorUtil.temperatureToColor(avgTemp);
                gc.setFill(baseColor.deriveColor(0, 1, 0.85, 0.72));
            } else {
                gc.setFill(Color.web("#1e293b", 0.55));
            }
            gc.fill();

            gc.setStroke(Color.web("#94a3b8", 0.30));
            gc.setLineWidth(1);
            gc.stroke();
        }
    }

    private void drawTerrainHints(GraphicsContext gc) {
        // Alpi — oval semi-transparent în centrul Europei
        Point2D alps = EuropeMapData.geoToCanvas(46.5, 10.0, CANVAS_W, CANVAS_H, MAP_PAD);
        gc.setFill(Color.web("#5D4037", 0.18));
        gc.fillOval(alps.getX() - 60, alps.getY() - 35, 120, 70);

        // Pirinei — oval în sud-vest
        Point2D pyrenees = EuropeMapData.geoToCanvas(42.5, 1.0, CANVAS_W, CANVAS_H, MAP_PAD);
        gc.setFill(Color.web("#5D4037", 0.15));
        gc.fillOval(pyrenees.getX() - 40, pyrenees.getY() - 25, 80, 50);

        // Munții Scandiinavi — oval în nord
        Point2D scandinavia = EuropeMapData.geoToCanvas(62.0, 8.0, CANVAS_W, CANVAS_H, MAP_PAD);
        gc.setFill(Color.web("#5D4037", 0.15));
        gc.fillOval(scandinavia.getX() - 50, scandinavia.getY() - 30, 100, 60);
    }

    /* ============================================================
       Desenare orașe — marcatori statici 2.5D
       ============================================================ */

    private void drawCityStatic(GraphicsContext gc, MapData md, double x, double y) {
        Color baseColor = ColorUtil.temperatureToColor(md.getTempMax());
        Color darkerColor = baseColor.darker();

        // Înălțime bară de extruziune 2.5D (capată la 60 px)
        double barHeight = Math.min(60, Math.abs(md.getTempMax()) / 2.0);
        double barW = 6;

        // Umbră de adâncime sub cerc
        gc.setFill(Color.rgb(0, 0, 0, 0.35));
        gc.fillOval(x - 6, y - 5, 16, 16);

        // Bară de extruziune verticală
        if (barHeight > 2) {
            gc.setFill(darkerColor);
            gc.fillRect(x - barW / 2, y - 8 - barHeight, barW, barHeight);
        }

        // Cerc bază (raza 8px)
        gc.setFill(baseColor);
        gc.fillOval(x - 8, y - 8, 16, 16);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeOval(x - 8, y - 8, 16, 16);

        // Badge temperatură — dreptunghi rotunjit deasupra extruziunii
        String tempText = Math.round(md.getTempMin()) + "° / " + Math.round(md.getTempMax()) + "°";
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        double textW = gc.getFont().getSize() * tempText.length() * 0.65;
        double badgeW = Math.max(36, textW + 10);
        double badgeH = 16;
        double badgeY = y - 8 - barHeight - badgeH - 3;

        gc.setFill(Color.rgb(15, 23, 42, 0.88));
        gc.fillRoundRect(x - badgeW / 2, badgeY, badgeW, badgeH, 8, 8);
        gc.setStroke(Color.web("#94a3b8", 0.4));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x - badgeW / 2, badgeY, badgeW, badgeH, 8, 8);

        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(tempText, x, badgeY + badgeH / 2 + 1);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);

        // Iconiță meteo statică
        drawWeatherIconStatic(gc, md.getPictograma(), x, y, baseColor);
    }

    private void drawWeatherIconStatic(GraphicsContext gc, String pictograma, double x, double y, Color baseColor) {
        if (pictograma == null) return;
        String p = pictograma.toLowerCase();

        double iconY = y - 30;

        if (p.contains("soare") || p.contains("sun") || p.contains("sunny")) {
            // Soare — cerc galben (razele sunt animate în stratul dinamic)
            gc.setFill(Color.web("#fbbf24"));
            gc.fillOval(x + 10, iconY, 10, 10);
        } else if (p.contains("ploaie") || p.contains("rain")) {
            // Ploaie — 3 linii albastre sub marcator
            gc.setStroke(Color.web("#60a5fa"));
            gc.setLineWidth(2);
            gc.strokeLine(x - 5, y + 12, x - 5, y + 18);
            gc.strokeLine(x, y + 12, x, y + 18);
            gc.strokeLine(x + 5, y + 12, x + 5, y + 18);
        } else if (p.contains("ninsoare") || p.contains("snow")) {
            // Ninsoare — fulgi mici albi
            gc.setFill(Color.WHITE);
            gc.fillOval(x + 8, iconY + 2, 4, 4);
            gc.fillOval(x + 12, iconY + 5, 3, 3);
            gc.fillOval(x + 6, iconY + 6, 3, 3);
        } else if (p.contains("furtuna") || p.contains("storm") || p.contains("thunder")) {
            // Furtună — nor violet închis deasupra marcatorului
            gc.setFill(Color.web("#7c3aed", 0.8));
            gc.fillOval(x + 6, iconY, 14, 10);
        } else {
            // Nor — implicit (cerc pufos alb)
            gc.setFill(Color.web("#e2e8f0", 0.85));
            gc.fillOval(x + 6, iconY, 14, 10);
            gc.fillOval(x + 10, iconY - 3, 10, 8);
            gc.fillOval(x + 4, iconY - 2, 10, 8);
        }
    }

    /* ============================================================
       Strat dinamic — animații meteo per cadru
       ============================================================ */

    private void drawDynamicLayer(long now) {
        dynamicGc.clearRect(0, 0, CANVAS_W, CANVAS_H);

        // Actualizează unghiul de rotație al razelor soarelui
        sunRotationAngle += 0.025;

        // Nori globali care se deplasează
        drawDriftingClouds(dynamicGc);

        // Animații per oraș
        for (MapData md : currentMapData) {
            Point2D p = EuropeMapData.geoToCanvas(
                md.getLatitudine(), md.getLongitudine(), CANVAS_W, CANVAS_H, MAP_PAD);
            String key = md.getOras();
            CityAnimState state = cityAnimStates.get(key);
            if (state == null) continue;

            String pict = md.getPictograma() == null ? "" : md.getPictograma().toLowerCase();

            if (pict.contains("ploaie") || pict.contains("rain")) {
                drawRain(dynamicGc, state, p.getX(), p.getY());
            } else if (pict.contains("ninsoare") || pict.contains("snow")) {
                drawSnow(dynamicGc, state);
            } else if (pict.contains("soare") || pict.contains("sun") || pict.contains("sunny")) {
                drawSunRays(dynamicGc, p.getX() + 15, p.getY() - 25);
            } else if (pict.contains("furtuna") || pict.contains("storm") || pict.contains("thunder")) {
                drawStorm(dynamicGc, state, p.getX(), p.getY(), now);
            }
        }
    }

    private void drawDriftingClouds(GraphicsContext gc) {
        gc.setFill(Color.web("#f1f5f9", 0.12));
        for (DriftingCloud cloud : clouds) {
            cloud.x += cloud.speed;
            if (cloud.x > CANVAS_W + cloud.w) {
                cloud.x = -cloud.w;
            }
            // Desenează norul ca 3 ovale suprapuse pentru aspect "pufos"
            gc.fillOval(cloud.x, cloud.y, cloud.w, cloud.h);
            gc.fillOval(cloud.x + cloud.w * 0.25, cloud.y - cloud.h * 0.2, cloud.w * 0.7, cloud.h * 0.8);
            gc.fillOval(cloud.x + cloud.w * 0.15, cloud.y + cloud.h * 0.1, cloud.w * 0.6, cloud.h * 0.7);
        }
    }

    private void drawRain(GraphicsContext gc, CityAnimState state, double cx, double cy) {
        gc.setStroke(Color.web("#60a5fa", 0.85));
        gc.setLineWidth(1.5);
        for (RainDrop drop : state.rainDrops) {
            drop.update(rand);
            gc.strokeLine(drop.baseX + drop.relX, drop.y,
                          drop.baseX + drop.relX, drop.y + 5);
        }
    }

    private void drawSnow(GraphicsContext gc, CityAnimState state) {
        gc.setFill(Color.WHITE);
        for (SnowFlake flake : state.snowFlakes) {
            flake.update(rand);
            gc.fillOval(flake.getX(), flake.y, 2.5, 2.5);
        }
    }

    private void drawSunRays(GraphicsContext gc, double sx, double sy) {
        gc.setStroke(Color.web("#fbbf24", 0.7));
        gc.setLineWidth(1.2);
        double rayLen = 12;
        for (int i = 0; i < 8; i++) {
            double angle = sunRotationAngle + (i * Math.PI / 4);
            double x1 = sx + 5 + Math.cos(angle) * 7;
            double y1 = sy + 5 + Math.sin(angle) * 7;
            double x2 = sx + 5 + Math.cos(angle) * rayLen;
            double y2 = sy + 5 + Math.sin(angle) * rayLen;
            gc.strokeLine(x1, y1, x2, y2);
        }
    }

    private void drawStorm(GraphicsContext gc, CityAnimState state, double cx, double cy, long now) {
        // Puls de strălucire violet în jurul marcatorului
        state.storm.glowPhase += 0.06;
        double glow = 0.25 + 0.15 * Math.sin(state.storm.glowPhase);
        gc.setFill(Color.web("#a855f7", glow));
        gc.fillOval(cx - 14, cy - 14, 28, 28);

        // Fulger ocazional (la intervale aleatoare de ~1.5–3.5 secunde)
        if (!state.storm.activeFlash) {
            long threshold = 1_500_000_000L + rand.nextInt(2_000_000_000);
            if (now - state.storm.lastFlashNs > threshold) {
                state.storm.activeFlash = true;
                state.storm.flashOpacity = 1.0;
                state.storm.lastFlashNs = now;
            }
        } else {
            state.storm.flashOpacity -= 0.08;
            if (state.storm.flashOpacity <= 0) {
                state.storm.activeFlash = false;
                state.storm.flashOpacity = 0;
            }
        }

        if (state.storm.activeFlash) {
            gc.setStroke(Color.color(1, 1, 1, state.storm.flashOpacity));
            gc.setLineWidth(2);
            // Zig-zag fulger
            double lx = cx + 6;
            double ly = cy - 28;
            gc.beginPath();
            gc.moveTo(lx, ly);
            gc.lineTo(lx + 4, ly + 6);
            gc.lineTo(lx - 2, ly + 12);
            gc.lineTo(lx + 5, ly + 18);
            gc.lineTo(lx + 1, ly + 24);
            gc.stroke();
        }
    }

    /* ============================================================
       Panou legendă
       ============================================================ */

    private VBox buildLegend() {
        VBox legend = new VBox(10);
        legend.getStyleClass().add("glass-card");
        legend.setMaxWidth(210);
        legend.setPadding(new Insets(12));

        Label title = new Label("Legenda");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");

        // Scara de temperatură — gradient orizontal
        Canvas tempCanvas = new Canvas(170, 28);
        GraphicsContext tgc = tempCanvas.getGraphicsContext2D();
        LinearGradient grad = new LinearGradient(0, 0, 1, 0, true, null,
            new Stop(0, ColorUtil.temperatureToColor(-20)),
            new Stop(0.5, ColorUtil.temperatureToColor(10)),
            new Stop(1, ColorUtil.temperatureToColor(40)));
        tgc.setFill(grad);
        tgc.fillRoundRect(0, 10, 170, 12, 6, 6);
        tgc.setFill(Color.WHITE);
        tgc.setFont(Font.font(9));
        tgc.fillText("-20°C", 0, 7);
        tgc.fillText("0°C", 75, 7);
        tgc.fillText("40°C", 140, 7);

        // Simboluri meteo
        VBox symbols = new VBox(6);
        symbols.getChildren().addAll(
            createLegendRow("Soare", Color.web("#fbbf24")),
            createLegendRow("Nor", Color.web("#e2e8f0")),
            createLegendRow("Ploaie", Color.web("#60a5fa")),
            createLegendRow("Ninsoare", Color.WHITE),
            createLegendRow("Furtună", Color.web("#a855f7"))
        );

        legend.getChildren().addAll(title, tempCanvas, symbols);
        return legend;
    }

    private HBox createLegendRow(String text, Color color) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Canvas dot = new Canvas(12, 12);
        GraphicsContext dgc = dot.getGraphicsContext2D();
        dgc.setFill(color);
        dgc.fillOval(1, 1, 10, 10);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 11px;");
        row.getChildren().addAll(dot, lbl);
        return row;
    }

    /* ============================================================
       Auto-reîmprospătare la fiecare 60 secunde
       ============================================================ */

    private void startAutoRefresh() {
        refreshTimeline = new Timeline(
            new KeyFrame(Duration.seconds(60), e -> fadeAndRefresh()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void fadeAndRefresh() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(600), canvasLayers);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            loadData();
            FadeTransition fadeIn = new FadeTransition(Duration.millis(600), canvasLayers);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        fadeOut.play();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Eroare");
        boolean isHeadless = java.awt.GraphicsEnvironment.isHeadless()
            || System.getenv("HEADLESS") != null;
        if (isHeadless) {
            alert.show();
        } else {
            alert.showAndWait();
        }
    }
}

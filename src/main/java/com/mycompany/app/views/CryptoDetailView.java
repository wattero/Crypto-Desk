package com.mycompany.app.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.BarChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.GridPane;
import com.mycompany.app.models.Crypto;
import com.mycompany.app.models.HistoricalData;

import com.mycompany.app.models.ChartPoint;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javafx.util.StringConverter;
import javafx.scene.chart.CategoryAxis;

public class CryptoDetailView extends VBox {
    private Label titleLabel;
    private Label priceLabel;
    private Label changeLabel;
    private LineChart<Number, Number> priceChart;
    private BarChart<String, Number> volumeChart;
    private VBox chartHolder;
    private ToggleButton priceToggle;
    private ToggleButton volumeToggle;
    private boolean showingVolume = false;
    private final String[] timeIntervals = { "1D", "1W", "1M", "3M", "1Y" };
    private final java.util.List<Button> intervalButtons = new java.util.ArrayList<>();
    private final java.util.Map<String, Button> intervalButtonMap = new java.util.HashMap<>();
    private Button selectedIntervalButton = null;
    private Button refreshButton;

    private final Label marketCapValue = new Label();
    private final Label volumeValue = new Label();
    private final Label circulatingSupplyValue = new Label();

    // Callback for when user selects a time interval
    private Consumer<String> onIntervalSelected;
    // Callback for refresh button
    private Runnable onRefreshRequested;

    public CryptoDetailView() {
        super(20);
        setPadding(new Insets(25));
        getStyleClass().add("detail-pane");

        HBox header = new HBox(10);
        titleLabel = new Label("Bitcoin (BTC)");
        titleLabel.getStyleClass().add("detail-header");
        header.getChildren().add(titleLabel);

        VBox priceInfo = new VBox(4);
        priceLabel = new Label("");
        priceLabel.getStyleClass().add("detail-price");
        changeLabel = new Label("");
        priceInfo.getChildren().addAll(priceLabel, changeLabel);

        priceChart = createPriceChart();
        volumeChart = createVolumeChart();
        chartHolder = new VBox();
        chartHolder.getChildren().add(priceChart);
        VBox.setVgrow(chartHolder, Priority.ALWAYS);

        HBox toggles = createChartToggle();
        HBox intervals = createTimeIntervalButtons();
        GridPane infoGrid = createInfoGrid();
        refreshButton = createRefreshButton();

        // add toggle above the chart holder
        getChildren().addAll(header, priceInfo, toggles, chartHolder, intervals, infoGrid, refreshButton);
    }
    
    private Button createRefreshButton() {
        Button button = new Button("Refresh Data");
        button.getStyleClass().add("refresh-button");
        button.setVisible(false); // Hidden by default, shown when there are failed loads
        button.setOnAction(e -> {
            if (onRefreshRequested != null) {
                onRefreshRequested.run();
            }
        });
        return button;
    }
    
    /**
     * Set callback for refresh button
     */
    public void setOnRefreshRequested(Runnable callback) {
        this.onRefreshRequested = callback;
    }
    
    /**
     * Show/hide refresh button
     */
    public void setRefreshButtonVisible(boolean visible) {
        if (refreshButton != null) {
            refreshButton.setVisible(visible);
        }
    }

    private LineChart<Number, Number> createPriceChart() {
        NumberAxis x = new NumberAxis();
        NumberAxis y = new NumberAxis();
        x.setVisible(false);
        y.setVisible(false);
        y.setForceZeroInRange(false);
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setCreateSymbols(false);
        chart.getStyleClass().add("crypto-chart");
        chart.setPrefHeight(300);
        chart.setLegendVisible(false);
        return chart;
    }

    private BarChart<String, Number> createVolumeChart() {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        x.setVisible(true);
        y.setVisible(true);
        y.setForceZeroInRange(true);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.getStyleClass().add("crypto-chart");
        chart.setPrefHeight(300);
        chart.setLegendVisible(false);
        chart.setCategoryGap(1);
        chart.setBarGap(0);
        x.setTickLabelRotation(45);
        x.setTickMarkVisible(true);
        x.setTickLabelsVisible(true);
        chart.setAnimated(false);
        return chart;
    }

    private HBox createChartToggle() {
        HBox box = new HBox(10);
        ToggleGroup group = new ToggleGroup();
        priceToggle = new ToggleButton("Price");
        priceToggle.setToggleGroup(group);
        priceToggle.getStyleClass().add("chart-toggle-button");
        priceToggle.setSelected(true);
        priceToggle.setOnAction(e -> setShowingVolume(false));

        volumeToggle = new ToggleButton("Volume");
        volumeToggle.setToggleGroup(group);
        volumeToggle.getStyleClass().add("chart-toggle-button");
        volumeToggle.setOnAction(e -> setShowingVolume(true));

        // Set initial selected style
        priceToggle.getStyleClass().add("chart-toggle-selected");

        box.getChildren().addAll(priceToggle, volumeToggle);
        return box;
    }

    private HBox createTimeIntervalButtons() {
        HBox buttonBox = new HBox(10);
        for (String interval : timeIntervals) {
            Button button = new Button(interval);
            button.getStyleClass().add("time-interval-button");
            button.setOnAction(e -> selectInterval(button, interval));
            intervalButtons.add(button);
            intervalButtonMap.put(interval, button);
            buttonBox.getChildren().add(button);
            
            // Start with disabled state (except 1D which is loaded first)
            if (!interval.equals("1D")) {
                setIntervalEnabled(interval, false);
            }
        }

        if (!intervalButtons.isEmpty()) {
            selectInterval(intervalButtons.get(0), timeIntervals[0]);
        }
        return buttonBox;
    }
    
    /**
     * Enable/disable a time interval button based on whether its data is loaded
     */
    public void setIntervalEnabled(String interval, boolean enabled) {
        Button button = intervalButtonMap.get(interval);
        if (button != null) {
            if (enabled) {
                button.setDisable(false);
                button.setOpacity(1.0);
                button.getStyleClass().remove("time-interval-button-disabled");
            } else {
                button.setDisable(true);
                button.setOpacity(0.5);
                button.getStyleClass().add("time-interval-button-disabled");
            }
        }
    }

    private GridPane createInfoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(40);
        grid.setVgap(15);
        grid.getStyleClass().add("info-grid");

        grid.add(new Label("Market Cap"), 0, 0);
        grid.add(marketCapValue, 1, 0);
        grid.add(new Label("Volume"), 0, 1);
        grid.add(volumeValue, 1, 1);
        grid.add(new Label("Circulating Supply"), 0, 2);
        grid.add(circulatingSupplyValue, 1, 2);

        grid.getChildren().forEach(node -> {
            if (node instanceof Label) {
                if (GridPane.getColumnIndex(node) == 0) {
                    node.getStyleClass().add("info-label");
                } else {
                    node.getStyleClass().add("info-value");
                }
            }
        });
        return grid;
    }

    /**
     * Set callback for when user selects a time interval
     */
    public void setOnIntervalSelected(Consumer<String> callback) {
        this.onIntervalSelected = callback;
    }

    /**
     * Display a cryptocurrency's basic information
     */
    public void displayCrypto(Crypto crypto) {
        if (crypto == null) {
            clear();
            return;
        }

        titleLabel.setText(crypto.getName() + " (" + crypto.getSymbol() + ")");
        priceLabel.setText(crypto.getPriceFormatted());
        changeLabel.setText(crypto.getChangeFormatted());
        changeLabel.getStyleClass().removeAll("positive-change", "negative-change");
        changeLabel.getStyleClass().add(crypto.getChangePercent() >= 0 ? "positive-change" : "negative-change");

        marketCapValue.setText(crypto.getMarketCap());
        volumeValue.setText(crypto.getVolume());
        circulatingSupplyValue.setText(crypto.getCirculatingSupply());

        // 1D data is always loaded first, so enable it
        setIntervalEnabled("1D", true);

        // Check if currently selected interval is available for this crypto
        // If not, fall back to 1D
        if (selectedIntervalButton != null && !selectedIntervalButton.isDisable()) {
            String interval = selectedIntervalButton.getText();
            // Try to use selected interval - controller will fall back to 1D if not available
            if (onIntervalSelected != null) {
                onIntervalSelected.accept(interval);
            }
        } else {
            // If no interval is selected or selected is disabled, select 1D
            selectIntervalIfEnabled("1D");
        }
    }
    
    /**
     * Select an interval if it's enabled, otherwise fall back to 1D
     */
    public void selectIntervalIfEnabled(String interval) {
        Button button = intervalButtonMap.get(interval);
        if (button != null && !button.isDisable()) {
            selectInterval(button, interval);
        } else {
            // Fall back to 1D
            Button oneDayButton = intervalButtonMap.get("1D");
            if (oneDayButton != null && !oneDayButton.isDisable()) {
                selectInterval(oneDayButton, "1D");
            }
        }
    }

    /**
     * Update chart with historical data from controller
     */
    public void updateChartData(HistoricalData hd) {
        if (hd == null || hd.getPoints().isEmpty()) {
            return;
        }

        // Determine the interval based on current selection
        String interval = selectedIntervalButton != null ? selectedIntervalButton.getText() : "1D";
        String days = convertIntervalToDays(interval);

        final boolean useVolume = this.showingVolume;
        final DateTimeFormatter formatter = chooseFormatter(days);

        XYChart.Series<Number, Number> priceSeries = new XYChart.Series<>();
        XYChart.Series<String, Number> volumeSeries = new XYChart.Series<>();

        long minX = Long.MAX_VALUE, maxX = Long.MIN_VALUE;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        for (ChartPoint p : hd.getPoints()) {
            Double value = useVolume ? p.getVolume() : p.getPrice();
            if (value == null)
                continue;
            long x = p.getEpochMilli();

            // Format timestamp for both charts
            String formattedTime = formatter.format(Instant.ofEpochMilli(x));

            if (useVolume) {
                volumeSeries.getData().add(new XYChart.Data<>(formattedTime, value));
            } else {
                priceSeries.getData().add(new XYChart.Data<>(x, value));
            }

            if (x < minX)
                minX = x;
            if (x > maxX)
                maxX = x;
            if (value < minY)
                minY = value;
            if (value > maxY)
                maxY = value;
        }

        final long fMinX = minX, fMaxX = maxX;
        final double fMinY = minY, fMaxY = maxY;
        Platform.runLater(() -> {
            // choose which chart to populate
            if (useVolume) {
                priceToggle.setSelected(false);
                volumeToggle.setSelected(true);
                if (!chartHolder.getChildren().contains(volumeChart)) {
                    chartHolder.getChildren().setAll(volumeChart);
                }
                volumeChart.getData().clear();

                ((CategoryAxis) volumeChart.getXAxis()).getCategories().clear();

                // Sample the data to reduce label crowding
                XYChart.Series<String, Number> sampledSeries = new XYChart.Series<>();
                int sampleRate = calculateSampleRate(volumeSeries.getData().size(), days);
                for (int i = 0; i < volumeSeries.getData().size(); i += sampleRate) {
                    sampledSeries.getData().add(volumeSeries.getData().get(i));
                }
                volumeChart.getData().add(sampledSeries);
            } else {
                priceToggle.setSelected(true);
                volumeToggle.setSelected(false);
                if (!chartHolder.getChildren().contains(priceChart)) {
                    chartHolder.getChildren().setAll(priceChart);
                }
                priceChart.getData().clear();
                priceChart.getData().add(priceSeries);
            }

            // adjust Y axis to min/max with padding
            NumberAxis yAxis = (NumberAxis) (useVolume ? volumeChart.getYAxis() : priceChart.getYAxis());

            if (useVolume) {
                // For volume, always start at 0
                yAxis.setAutoRanging(false);
                yAxis.setLowerBound(0);
                // Add some padding to the top
                double upperY = fMaxY * 1.1;
                if (upperY <= 0)
                    upperY = 100; // default if no data
                yAxis.setUpperBound(upperY);
                yAxis.setTickUnit(upperY / 5.0);

                // Format tick labels
                yAxis.setTickLabelFormatter(new StringConverter<Number>() {
                    @Override
                    public String toString(Number object) {
                        return formatVolume(object.doubleValue());
                    }

                    @Override
                    public Number fromString(String string) {
                        return 0;
                    }
                });
            } else if (!Double.isInfinite(fMinY) && !Double.isInfinite(fMaxY)) {
                // Price chart logic (unchanged mostly)
                double lowerY = fMinY;
                double upperY = fMaxY;
                double padding = (upperY - lowerY) * 0.10;
                if (padding == 0)
                    padding = Math.max(1.0, upperY * 0.05);
                lowerY = lowerY - padding;
                upperY = upperY + padding;
                yAxis.setAutoRanging(false);
                yAxis.setLowerBound(lowerY);
                yAxis.setUpperBound(upperY);
                yAxis.setTickUnit(Math.max(1.0, (upperY - lowerY) / 8.0));
                yAxis.setForceZeroInRange(false);
                // Reset formatter for price chart if needed (though it's usually default)
                yAxis.setTickLabelFormatter(null);
            } else {
                yAxis.setAutoRanging(true);
            }

            // For price chart, handle X axis
            if (!useVolume) {
                NumberAxis xAxis = (NumberAxis) priceChart.getXAxis();
                xAxis.setTickLabelFormatter(new StringConverter<Number>() {

                    @Override
                    public String toString(Number object) {
                        if (object == null)
                            return "";
                        try {
                            return formatter.format(Instant.ofEpochMilli(object.longValue()));
                        } catch (Exception e) {
                            return Long.toString(object.longValue());
                        }
                    }

                    @Override
                    public Number fromString(String string) {
                        return null;
                    }
                });

                // adjust X axis to the data range
                if (fMinX != Long.MAX_VALUE && fMaxX != Long.MIN_VALUE) {

                    double lowerX = fMinX;
                    double upperX = fMaxX;
                    if (lowerX == upperX) {
                        // single point — give a 1-hour window
                        lowerX = lowerX - 3_600_000;
                        upperX = upperX + 3_600_000;
                    }
                    xAxis.setAutoRanging(false);
                    xAxis.setLowerBound(lowerX);
                    xAxis.setUpperBound(upperX);
                    double tick = Math.max(1.0,
                            (upperX - lowerX) / 6.0);
                    xAxis.setTickUnit(tick);
                } else {
                    xAxis.setAutoRanging(true);
                }
            } else {
                CategoryAxis xAxis = (CategoryAxis) volumeChart
                        .getXAxis();
                xAxis.setAutoRanging(true);
                xAxis.setTickMarkVisible(true);
            }
        });
    }

    private String convertIntervalToDays(String interval) {
        return switch (interval) {
            case "1D" -> "1";
            case "1W" -> "7";
            case "1M" -> "30";
            case "3M" -> "90";
            case "1Y" -> "365";
            default -> "1";
        };
    }

    private void setShowingVolume(boolean v) {
        if (this.showingVolume == v)
            return;
        this.showingVolume = v;

        if (v) {
            volumeToggle.getStyleClass().add("chart-toggle-selected");
            priceToggle.getStyleClass().remove("chart-toggle-selected");
        } else {
            priceToggle.getStyleClass().add("chart-toggle-selected");
            volumeToggle.getStyleClass().remove("chart-toggle-selected");
        }
        // Request new data from controller
        String interval = (selectedIntervalButton != null) ? selectedIntervalButton.getText() : "1D";
        if (onIntervalSelected != null) {
            onIntervalSelected.accept(interval);
        }
    }

    private void selectInterval(Button button, String interval) {
        // Don't allow selection if button is disabled
        if (button.isDisable()) {
            return;
        }
        
        if (selectedIntervalButton != null) {
            selectedIntervalButton.getStyleClass().remove("time-interval-selected");
        }
        selectedIntervalButton = button;
        if (selectedIntervalButton != null) {
            selectedIntervalButton.getStyleClass().add("time-interval-selected");
        }
        // Notify controller about interval change
        if (onIntervalSelected != null) {
            onIntervalSelected.accept(interval);
        }
    }

    private void clear() {
        titleLabel.setText("Select a crypto");
        priceLabel.setText("");
        changeLabel.setText("");
        priceChart.getData().clear();
        volumeChart.getData().clear();
        marketCapValue.setText("");
        volumeValue.setText("");
        circulatingSupplyValue.setText("");
    }

    private static DateTimeFormatter chooseFormatter(String days) {
        try {
            int d = Integer.parseInt(days);
            if (d <= 1) {
                return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
            } else if (d <= 7) {
                return DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault());
            } else if (d <= 90) {
                return DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());
            } else if (d <= 365) {
                return DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());
            } else {
                return DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault());
            }
        } catch (Exception ex) {
            return DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());
        }
    }

    private int calculateSampleRate(int totalPoints, String days) {
        int targetLabels = 50;
        try {
            if (totalPoints <= targetLabels) {
                return 1;
            }

            int d = Integer.parseInt(days);

            if (d <= 1)
                return Math.max(1, totalPoints / targetLabels);
            else if (d <= 7)
                return 1; // Sample every point for 1W interval
            else if (d <= 30)
                return 2; // Sample every 2nd point for 1M interval
            else if (d <= 90)
                return 3; // Sample every 3rd point for 3M interval
            else
                return 6; // Sample every 6th point for 1Y interval
        } catch (Exception e) {
            // Default to no sampling on error
            return 1;
        }
    }

    private String formatVolume(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0)
            return "0";
        double abs = Math.abs(value);
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.##");
        if (abs >= 1_000_000_000_000.0) {
            return df.format(value / 1_000_000_000_000.0) + "T";
        } else if (abs >= 1_000_000_000.0) {
            return df.format(value / 1_000_000_000.0) + "B";
        } else if (abs >= 1_000_000.0) {
            return df.format(value / 1_000_000.0) + "M";
        } else if (abs >= 1_000.0) {
            return df.format(value / 1_000.0) + "K";
        } else {
            return df.format(value);
        }
    }

}

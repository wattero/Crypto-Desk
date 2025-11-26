package com.mycompany.app.views;

import com.mycompany.app.models.News;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.util.List;
import java.util.function.Consumer;

public class NewsView extends VBox {
    private final VBox newsList = new VBox(12);
    private final ToggleButton allToggle;
    private final ToggleButton selectedToggle;
    private HostServices hostServices;
    private static final int SKELETON_COUNT = 4;

    public NewsView(Consumer<Boolean> onToggleChanged) {
        super(15);
        setPadding(new Insets(25));
        setPrefWidth(350);
        getStyleClass().add("news-pane");

        Label header = new Label("News & Insights");
        header.getStyleClass().add("section-header");

        ToggleGroup toggleGroup = new ToggleGroup();
        allToggle = new ToggleButton("All");
        allToggle.setToggleGroup(toggleGroup);
        allToggle.setSelected(true);
        allToggle.getStyleClass().add("news-toggle");

        selectedToggle = new ToggleButton("Selected");
        selectedToggle.setToggleGroup(toggleGroup);
        selectedToggle.getStyleClass().add("news-toggle");

        HBox toggleBox = new HBox(allToggle, selectedToggle);
        toggleBox.getStyleClass().add("news-toggle-box");

        toggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null && onToggleChanged != null) {
                onToggleChanged.accept(allToggle.isSelected());
            }
        });

        ScrollPane scrollPane = new ScrollPane(newsList);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("news-scroll-pane");

        getChildren().addAll(header, toggleBox, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public boolean isShowAllSelected() {
        return allToggle.isSelected();
    }

    /**
     * Show skeleton loading cards while news is being fetched
     */
    public void showLoading() {
        newsList.getChildren().clear();
        for (int i = 0; i < SKELETON_COUNT; i++) {
            newsList.getChildren().add(createSkeletonCard());
        }
    }

    public void updateNews(List<News> news) {
        newsList.getChildren().clear();
        if (news == null) return;
        for (News n : news) {
            newsList.getChildren().add(createNewsCard(n));
        }
    }

    /**
     * Create a skeleton loading card with animated shimmer effect
     */
    private VBox createSkeletonCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("news-card");

        // Skeleton title (two lines)
        Region titleLine1 = createSkeletonLine(0.9);
        Region titleLine2 = createSkeletonLine(0.6);

        // Skeleton metadata
        Region metadataLine = createSkeletonLine(0.4);
        metadataLine.setMaxHeight(12);
        metadataLine.setMinHeight(12);

        // Skeleton summary
        VBox summaryContainer = new VBox(4);
        summaryContainer.setPadding(new Insets(5, 0, 5, 0));
        Region summaryLine1 = createSkeletonLine(1.0);
        Region summaryLine2 = createSkeletonLine(0.85);
        summaryLine1.setMaxHeight(11);
        summaryLine1.setMinHeight(11);
        summaryLine2.setMaxHeight(11);
        summaryLine2.setMinHeight(11);
        summaryContainer.getChildren().addAll(summaryLine1, summaryLine2);

        card.getChildren().addAll(titleLine1, titleLine2, metadataLine, summaryContainer);
        return card;
    }

    /**
     * Create a single skeleton line with shimmer animation
     */
    private Region createSkeletonLine(double widthPercentage) {
        Region line = new Region();
        line.getStyleClass().add("skeleton-line");
        line.setMaxWidth(Double.MAX_VALUE);
        line.setMinHeight(16);
        line.setMaxHeight(16);
        
        // Set width as percentage of parent
        line.maxWidthProperty().bind(newsList.widthProperty().multiply(widthPercentage).subtract(20));
        
        // Add shimmer animation
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(line.opacityProperty(), 0.3)),
            new KeyFrame(Duration.millis(800), new KeyValue(line.opacityProperty(), 0.6)),
            new KeyFrame(Duration.millis(1600), new KeyValue(line.opacityProperty(), 0.3))
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        
        return line;
    }

    private VBox createNewsCard(News news) {
        VBox card = new VBox(5);
        card.getStyleClass().add("news-card");
        
        // Title (Headline)
        Label title = new Label(news.getTitle());
        title.getStyleClass().add("news-title");
        title.setWrapText(true);

        // Source and Date
        Label metadata = new Label(news.getSource() + " · " + news.getDate());
        metadata.getStyleClass().add("news-subtitle");
        metadata.setWrapText(false);

        // Summary/Snippet
        VBox summaryContainer = new VBox();
        if (news.getSummary() != null && !news.getSummary().isEmpty()) {
            Label summary = new Label(news.getSummary());
            summary.getStyleClass().add("news-summary");
            summary.setWrapText(true);
            summary.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 11;");
            summaryContainer.getChildren().add(summary);
            summaryContainer.setPadding(new Insets(5, 0, 5, 0));
        }

        // Read More Link
        VBox linkContainer = new VBox();
        if (news.getLink() != null && !news.getLink().isEmpty()) {
            Hyperlink readMore = new Hyperlink("Read full article");
            readMore.getStyleClass().add("news-link");
            readMore.setStyle("-fx-font-size: 11; -fx-text-fill: #4a9eff;");
            readMore.setOnAction(event -> {
                if (hostServices != null) {
                    hostServices.showDocument(news.getLink());
                }
            });
            linkContainer.getChildren().add(readMore);
        }

        card.getChildren().addAll(title, metadata);
        if (!summaryContainer.getChildren().isEmpty()) {
            card.getChildren().add(summaryContainer);
        }
        if (!linkContainer.getChildren().isEmpty()) {
            card.getChildren().add(linkContainer);
        }

        return card;
    }
}

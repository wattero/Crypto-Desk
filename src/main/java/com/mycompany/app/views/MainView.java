package com.mycompany.app.views;

import com.mycompany.app.models.Crypto;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.Hyperlink;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class MainView extends BorderPane {
    private final NewsView newsView;
    private final VBox cryptoListBox = new VBox(8);
    private HBox selectedSidebarItem = null;
    
    // Callback for when a crypto is selected
    private Consumer<Crypto> onCryptoSelected;

    public MainView(CryptoDetailView detailView, NewsView newsView) {
        this.newsView = newsView;

        setLeft(createSidebar());
        setCenter(detailView);
        setRight(newsView);

        getStyleClass().add("main-view");
    }
    
    /**
     * Set callback for when a crypto is selected
     */
    public void setOnCryptoSelected(Consumer<Crypto> callback) {
        this.onCryptoSelected = callback;
    }

    /**
     * Set HostServices for opening URLs in browser
     * This should be called from the Application class
     */
    public void setHostServices(HostServices hostServices) {
        this.newsView.setHostServices(hostServices);
    }
    
    /**
     * Load crypto list into sidebar
     */
    public void displayCryptos(List<Crypto> cryptos) {
        cryptoListBox.getChildren().clear();
        for (Crypto crypto : cryptos) {
            cryptoListBox.getChildren().add(createSidebarItem(crypto));
        }

        if (!cryptos.isEmpty()) {
            selectCrypto(cryptos.get(0), (HBox) cryptoListBox.getChildren().get(0));
        }
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(25));
        sidebar.setPrefWidth(300);
        sidebar.getStyleClass().add("sidebar");

        Label logo = new Label("CryptoDesk");
        logo.getStyleClass().add("logo");

        // Powered by CoinGecko API (CoinGecko API is a hyperlink)
        HBox poweredByBox = new HBox();
        poweredByBox.setSpacing(4);
        Label poweredByLabel = new Label("Powered by ");
        Hyperlink cgLink = new Hyperlink("CoinGecko API");
        cgLink.setOnAction(e -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI("https://www.coingecko.com/en/api"));
                }
            } catch (IOException | URISyntaxException ex) {
                // ignore navigation failures
            }
        });
        poweredByBox.getChildren().addAll(poweredByLabel, cgLink);
        poweredByBox.getStyleClass().add("powered-by");

        TextField searchField = new TextField();
        searchField.setPromptText("Search");
        searchField.getStyleClass().add("search-field");

        Label watchlistHeader = new Label("Watchlist");
        watchlistHeader.getStyleClass().add("watchlist-header");

        ScrollPane scrollPane = new ScrollPane(cryptoListBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("sidebar-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        sidebar.getChildren().addAll(logo, poweredByBox, searchField, watchlistHeader, scrollPane);
        return sidebar;
    }

    private HBox createSidebarItem(Crypto crypto) {
        HBox item = new HBox(10);
        item.setPadding(new Insets(12));
        item.getStyleClass().add("sidebar-item");

        VBox nameAndSymbol = new VBox(2);
        Label name = new Label(crypto.getName());
        name.getStyleClass().add("crypto-name-sidebar");
        Label symbol = new Label(crypto.getSymbol());
        symbol.getStyleClass().add("crypto-symbol-sidebar");
        nameAndSymbol.getChildren().addAll(name, symbol);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label priceLabel = new Label(crypto.getPriceFormatted());
        priceLabel.getStyleClass().add("sidebar-price");

        Label change = new Label(crypto.getChangeFormatted());
        change.getStyleClass().add(crypto.getChangePercent() >= 0 ? "positive-change" : "negative-change");

        VBox priceAndChange = new VBox(4);
        priceAndChange.setAlignment(Pos.CENTER_RIGHT);
        priceAndChange.getChildren().addAll(priceLabel, change);

        item.getChildren().addAll(nameAndSymbol, spacer, priceAndChange);
        item.setOnMouseClicked(e -> selectCrypto(crypto, item));
        return item;
    }

    private void selectCrypto(Crypto crypto, HBox item) {
        if (selectedSidebarItem != null) {
            selectedSidebarItem.getStyleClass().remove("sidebar-item-selected");
        }
        selectedSidebarItem = item;
        selectedSidebarItem.getStyleClass().add("sidebar-item-selected");
        
        // Notify controller about selection
        if (onCryptoSelected != null) {
            onCryptoSelected.accept(crypto);
        }
    }
}
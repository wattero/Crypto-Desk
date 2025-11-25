package com.mycompany.app.views;

import com.mycompany.app.models.Crypto;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.Hyperlink;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * View that renders the crypto list sidebar.
 */
public class CryptoListView extends VBox {
    private final VBox cryptoListBox = new VBox(8);
    private HBox selectedSidebarItem = null;
    private final java.util.Map<String, HBox> cryptoItems = new java.util.HashMap<>();
    private final java.util.Map<String, Label> priceLabels = new java.util.HashMap<>();
    private final java.util.Map<String, Label> changeLabels = new java.util.HashMap<>();

    // Callback for when a crypto is selected
    private Consumer<Crypto> onCryptoSelected;

    public CryptoListView() {
        super(20);
        setPadding(new Insets(25));
        setPrefWidth(300);
        getStyleClass().add("sidebar");

        Label logo = new Label("CryptoDesk");
        logo.getStyleClass().add("logo");

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

        Label watchlistHeader = new Label("Top 5 Cryptocurrencies");
        watchlistHeader.getStyleClass().add("watchlist-header");

        ScrollPane scrollPane = new ScrollPane(cryptoListBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("sidebar-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Label loadingLabel = new Label("Loading data...");
        loadingLabel.setStyle("-fx-text-fill: white; -fx-padding: 10;");
        loadingLabel.setMaxWidth(Double.MAX_VALUE);
        loadingLabel.setAlignment(Pos.CENTER);
        cryptoListBox.getChildren().add(loadingLabel);

        getChildren().addAll(logo, poweredByBox, watchlistHeader, scrollPane);
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
        // Currently CoinGecko link uses Desktop.browse; keep method for symmetry if we
        // want HostServices later
    }

    /**
     * Load crypto list into sidebar
     * All items start as disabled (grayed out) until their data is loaded
     */
    public void displayCryptos(List<Crypto> cryptos) {
        cryptoListBox.getChildren().clear();
        cryptoItems.clear();
        priceLabels.clear();
        changeLabels.clear();
        for (Crypto crypto : cryptos) {
            HBox item = createSidebarItem(crypto);
            cryptoListBox.getChildren().add(item);
            cryptoItems.put(crypto.getId(), item);
            // Start with disabled state (grayed out)
            setCryptoEnabled(crypto.getId(), false);
        }
    }

    /**
     * Enable/disable a crypto item based on whether its data is loaded
     */
    public void setCryptoEnabled(String cryptoId, boolean enabled) {
        HBox item = cryptoItems.get(cryptoId);
        if (item != null) {
            if (enabled) {
                item.getStyleClass().remove("sidebar-item-disabled");
                item.setDisable(false);
                // Make it brighter when enabled
                item.setOpacity(1.0);
            } else {
                item.getStyleClass().add("sidebar-item-disabled");
                item.setDisable(true);
                // Make it grayed out when disabled
                item.setOpacity(0.5);
            }
        }
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
        priceLabels.put(crypto.getId(), priceLabel);

        Label change = new Label(crypto.getChangeFormatted());
        change.getStyleClass().add(crypto.getChangePercent() >= 0 ? "positive-change" : "negative-change");
        changeLabels.put(crypto.getId(), change);

        VBox priceAndChange = new VBox(4);
        priceAndChange.setAlignment(Pos.CENTER_RIGHT);
        priceAndChange.getChildren().addAll(priceLabel, change);

        item.getChildren().addAll(nameAndSymbol, spacer, priceAndChange);
        item.setOnMouseClicked(e -> selectCrypto(crypto, item));
        return item;
    }

    /**
     * Update the displayed price and change for a crypto
     * Called by the price polling service
     * @param cryptoId The crypto ID
     * @param newPrice The new price
     * @param newChangePercent The new 24h change percentage
     */
    public void updatePrice(String cryptoId, double newPrice, double newChangePercent) {
        Label priceLabel = priceLabels.get(cryptoId);
        Label changeLabel = changeLabels.get(cryptoId);
        
        if (priceLabel != null) {
            priceLabel.setText(String.format("$%,.2f", newPrice));
        }
        
        if (changeLabel != null) {
            changeLabel.setText(String.format("%s%.2f%%", newChangePercent >= 0 ? "▲" : "▼", Math.abs(newChangePercent)));
            changeLabel.getStyleClass().removeAll("positive-change", "negative-change");
            changeLabel.getStyleClass().add(newChangePercent >= 0 ? "positive-change" : "negative-change");
        }
    }

    private void selectCrypto(Crypto crypto, HBox item) {
        // Don't allow selection if item is disabled
        if (item.isDisable()) {
            return;
        }

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

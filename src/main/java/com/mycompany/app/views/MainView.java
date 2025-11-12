package com.mycompany.app.views;

import javafx.application.HostServices;
import javafx.scene.layout.BorderPane;

/**
 * Main application layout, holding the crypto list sidebar, detail view, and news view.
 */
public class MainView extends BorderPane {
    private final NewsView newsView;

    public MainView(CryptoListView cryptoListView, CryptoDetailView detailView, NewsView newsView) {
        this.newsView = newsView;

        setLeft(cryptoListView);
        setCenter(detailView);
        setRight(newsView);

        getStyleClass().add("main-view");
    }

    /**
     * Set HostServices for opening URLs in browser
     * This should be called from the Application class
     */
    public void setHostServices(HostServices hostServices) {
        this.newsView.setHostServices(hostServices);
    }
}
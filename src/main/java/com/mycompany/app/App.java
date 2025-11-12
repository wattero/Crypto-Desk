package com.mycompany.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.mycompany.app.views.MainView;
import com.mycompany.app.views.CryptoDetailView;
import com.mycompany.app.views.NewsView;
import com.mycompany.app.controllers.MainController;
import com.mycompany.app.controllers.CryptoDetailController;
import com.mycompany.app.controllers.NewsController;
import com.mycompany.app.services.CryptoService;
import com.mycompany.app.services.SerpAPINewsService;
import java.util.List;
import com.mycompany.app.models.Crypto;

/**
 * Crypto Dashboard Application
 */
public class App extends Application {
    @Override
    public void start(Stage primaryStage) {
        // Initialize services
        CryptoService cryptoService = new CryptoService();
        SerpAPINewsService newsService = new SerpAPINewsService();
        
        // Initialize controllers
        CryptoDetailController detailController = new CryptoDetailController(cryptoService);
        NewsController newsController = new NewsController(newsService);
        MainController mainController = new MainController(cryptoService, detailController, newsController);
        
        // Initialize views
        CryptoDetailView detailView = new CryptoDetailView();
        NewsView newsView = new NewsView(mainController::handleNewsToggleChange);
        MainView mainView = new MainView(detailView, newsView);
        
        // Wire controllers to views
        detailController.setView(detailView);
        newsController.setView(newsView);
        
        // Set up view callbacks
        mainView.setOnCryptoSelected(mainController::selectCrypto);
        detailView.setOnIntervalSelected(interval -> {
            Crypto current = mainController.getSelectedCrypto();
            if (current != null) {
                detailController.selectTimeInterval(interval);
            }
        });
        
        // Set host services for opening URLs
        mainView.setHostServices(getHostServices());
        
        // Load initial data
        List<Crypto> cryptos = mainController.loadTopCryptos();
        mainView.displayCryptos(cryptos);
        
        // Create the main layout
        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
        root.getChildren().add(mainView);

        // Create the scene with dark theme
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        
        // Set up the primary stage
        primaryStage.setTitle("Crypto Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

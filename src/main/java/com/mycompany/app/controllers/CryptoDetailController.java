package com.mycompany.app.controllers;

import com.mycompany.app.models.Crypto;
import com.mycompany.app.models.HistoricalData;
import com.mycompany.app.services.CryptoService;
import com.mycompany.app.views.CryptoDetailView;

/**
 * Controller for the crypto detail view
 * Manages historical data fetching and chart updates
 */
public class CryptoDetailController {
    private final CryptoService cryptoService;
    private CryptoDetailView view;
    private Crypto currentCrypto;

    public CryptoDetailController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * Set the view that this controller manages
     */
    public void setView(CryptoDetailView view) {
        this.view = view;
    }

    /**
     * Display a cryptocurrency in the detail view
     */
    public void showCrypto(Crypto crypto) {
        this.currentCrypto = crypto;
        if (view != null) {
            view.displayCrypto(crypto);
        }
    }

    /**
     * Load historical data for a specific time interval
     */
    public HistoricalData loadHistoricalData(String cryptoId, String days) {
        return cryptoService.getHistoricalDataForCrypto(cryptoId, days);
    }

    /**
     * Handle time interval selection
     */
    public void selectTimeInterval(String interval) {
        if (currentCrypto == null || view == null) {
            return;
        }

        // Convert interval to days parameter
        String days = convertIntervalToDays(interval);
        
        // Fetch historical data
        HistoricalData data = loadHistoricalData(currentCrypto.getId(), days);
        
        // Update view with data
        if (data != null) {
            view.updateChartData(data);
        }
    }

    /**
     * Convert interval string (1D, 1W, etc.) to days parameter for API
     */
    private String convertIntervalToDays(String interval) {
        return switch (interval) {
            case "1D" -> "1";
            case "1W" -> "7";
            case "1M" -> "30";
            case "3M" -> "90";
            case "1Y" -> "365";
            case "All" -> "max";
            default -> "1";
        };
    }

    /**
     * Get the currently displayed crypto
     */
    public Crypto getCurrentCrypto() {
        return currentCrypto;
    }
}

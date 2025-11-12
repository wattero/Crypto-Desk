package com.mycompany.app.controllers;

import com.mycompany.app.models.Crypto;
import com.mycompany.app.services.CryptoService;
import java.util.List;

/**
 * Controller for the main view
 * Manages crypto list and coordinates between detail and news controllers
 */
public class MainController {
    private final CryptoService cryptoService;
    private final CryptoDetailController detailController;
    private final NewsController newsController;
    private Crypto selectedCrypto;

    public MainController(CryptoService cryptoService, 
                         CryptoDetailController detailController,
                         NewsController newsController) {
        this.cryptoService = cryptoService;
        this.detailController = detailController;
        this.newsController = newsController;
    }

    /**
     * Load the top cryptocurrencies
     */
    public List<Crypto> loadTopCryptos() {
        return cryptoService.getTopCryptos();
    }

    /**
     * Handle crypto selection from the sidebar
     */
    public void selectCrypto(Crypto crypto) {
        this.selectedCrypto = crypto;
        
        // Update detail view with selected crypto
        detailController.showCrypto(crypto);
        
        // Update news feed for selected crypto
        newsController.loadNewsForCrypto(crypto.getId());
    }

    /**
     * Handle news toggle change (All vs Selected)
     */
    public void handleNewsToggleChange(boolean showAll) {
        if (showAll) {
            newsController.loadGeneralNews();
        } else if (selectedCrypto != null) {
            newsController.loadNewsForCrypto(selectedCrypto.getId());
        }
    }

    /**
     * Get currently selected crypto
     */
    public Crypto getSelectedCrypto() {
        return selectedCrypto;
    }
}

package com.mycompany.app.controllers;

import com.mycompany.app.models.News;
import com.mycompany.app.services.SerpAPINewsService;
import com.mycompany.app.views.NewsView;
import java.util.List;

/**
 * Controller for the news view
 * Manages news fetching and filtering
 */
public class NewsController {
    private final SerpAPINewsService newsService;
    private NewsView view;

    public NewsController(SerpAPINewsService newsService) {
        this.newsService = newsService;
    }

    /**
     * Set the view that this controller manages
     */
    public void setView(NewsView view) {
        this.view = view;
    }

    /**
     * Load general cryptocurrency news
     */
    public void loadGeneralNews() {
        List<News> news = newsService.getGeneralNews();
        if (view != null) {
            view.updateNews(news);
        }
    }

    /**
     * Load news for a specific cryptocurrency
     */
    public void loadNewsForCrypto(String cryptoId) {
        List<News> news = newsService.getNewsForCrypto(cryptoId);
        if (view != null) {
            view.updateNews(news);
        }
    }
}

package com.mycompany.app.services;

import java.util.ArrayList;
import java.util.List;

import com.mycompany.app.models.News;

public class MockNewsService implements NewsService {
    @Override
    public List<News> getNewsForCrypto(String cryptoId) {
        List<News> list = new ArrayList<>();
        // Provide simple mock articles tailored by crypto id (basic examples)
        list.add(new News("Why " + cryptoId.toUpperCase() + " is moving today", "CryptoDaily", "1 hour ago"));
        list.add(new News("Market analysis for " + cryptoId.toUpperCase(), "MarketWatch", "3 hours ago"));
        list.add(new News("Investors look to " + cryptoId.toUpperCase(), "FinanceTimes", "8 hours ago"));
        return list;
    }
}

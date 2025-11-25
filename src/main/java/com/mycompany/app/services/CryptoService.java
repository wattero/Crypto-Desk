package com.mycompany.app.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.app.models.ChartPoint;
import com.mycompany.app.models.Crypto;
import com.mycompany.app.models.HistoricalData;

public class CryptoService {
    private static final String DEFAULT_API_URL = "https://api.coingecko.com/api/v3";
    private static final int TOP_N = 5;
    private static final String path = "/application.properties";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Properties props = loadProperties();
    private final CryptoCache cache;

    public CryptoService() {
        this.cache = new CryptoCache();
    }

    public CryptoService(CryptoCache cache) {
        this.cache = cache != null ? cache : new CryptoCache();
    }

    public List<Crypto> getTopCryptos() {
        // Check cache first (fast path)
        if (cache.hasTopCryptos()) {
            System.out.println("Returning top cryptos from cache");
            return cache.getTopCryptos();
        }

        synchronized (this) {
            // Check cache again inside lock (double-checked locking)
            if (cache.hasTopCryptos()) {
                System.out.println("Returning top cryptos from cache (synced)");
                return cache.getTopCryptos();
            }

            // Fetch from API
            System.out.println("Fetching top cryptos from API...");
            List<Crypto> cryptos = fetchTopCryptosFromAPI();
            System.out.println("Fetched " + (cryptos == null ? "null" : cryptos.size()) + " cryptos from API");

            // Store in cache
            if (cryptos != null && !cryptos.isEmpty()) {
                cache.setTopCryptos(cryptos);
            }

            return cryptos;
        }
    }

    private List<Crypto> fetchTopCryptosFromAPI() {
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        int maxRetries = 5; // Initial attempt + 4 retries
        // Retry delays: 10s, 20s, 30s, 30s (exponential backoff, then constant)
        int[] retryDelays = { 10000, 20000, 30000, 30000 };

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String url = String.format(
                        "%s/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=%d&page=1&sparkline=false&price_change_percentage=24h",
                        baseUrl, TOP_N);

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET();

                // Add API key header for Demo API
                String apiKey = props.getProperty("coingecko.api.key");
                if (apiKey != null && !apiKey.isBlank()) {
                    reqBuilder.header("x-cg-demo-api-key", apiKey);
                }

                HttpRequest request = reqBuilder.build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseCoinsJson(response.body());
                } else {
                    // Any non-2xx status code (including 429 rate limit) - retry with increasing
                    // delay
                    if (attempt < maxRetries - 1) {
                        int delay = retryDelays[attempt];
                        String statusMsg = response.statusCode() == 429 ? "Rate limit exceeded"
                                : "API returned status " + response.statusCode();
                        System.out.println(statusMsg + " for top cryptos. Waiting " + (delay / 1000)
                                + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return new ArrayList<>();
                        }
                        continue; // Retry the request
                    } else {
                        System.err.println("CoinGecko API returned non-2xx: " + response.statusCode());
                        return new ArrayList<>();
                    }
                }
            } catch (IOException | InterruptedException e) {
                // Network errors or interruptions - retry with increasing delay
                if (attempt < maxRetries - 1) {
                    int delay = retryDelays[attempt];
                    System.out.println("Network error for top cryptos (" + e.getMessage() + "). Waiting "
                            + (delay / 1000) + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new ArrayList<>();
                    }
                    continue; // Retry the request
                } else {
                    System.err.println("Failed to fetch CoinGecko data: " + e.getMessage());
                    return new ArrayList<>();
                }
            }
        }
        return new ArrayList<>();
    }

    private List<Crypto> parseCoinsJson(String json) throws IOException {
        List<Crypto> list = new ArrayList<>();
        JsonNode arr = mapper.readTree(json);
        if (!arr.isArray()) {
            System.err.println("parseCoinsJson: Root node is not an array");
            return new ArrayList<>();
        }

        for (JsonNode node : arr) {
            String id = node.path("id").asText("");
            String name = node.path("name").asText("");
            String symbol = node.path("symbol").asText("").toUpperCase();
            double price = node.path("current_price").asDouble(0.0);
            // CoinGecko returns price change percentage in requested field
            double changePct = node.path("price_change_percentage_24h").asDouble(0.0);
            double marketCapNum = node.path("market_cap").asDouble(0.0);
            double volumeNum = node.path("total_volume").asDouble(0.0);
            double circulating = node.path("circulating_supply").asDouble(0.0);

            String marketCap = formatMoneyShort(marketCapNum);
            String volume = formatMoneyShort(volumeNum);
            String circulatingStr = formatNumberShort(circulating);

            list.add(new Crypto(id, name, symbol, price, changePct, marketCap, volume, circulatingStr));
        }
        System.out.println("Parsed " + list.size() + " coins from JSON");
        return list;
    }

    /**
     * Check if historical data is available in cache for a specific crypto and
     * interval
     */
    public boolean hasHistoricalData(String id, String days) {
        if (id == null || id.isBlank() || days == null || days.isBlank()) {
            return false;
        }
        return cache.hasHistoricalData(id, days);
    }

    /**
     * fetch market_chart data for a single crypto id.
     * days accepts numeric day strings. Null/blank => "1".
     * Checks cache first before making API call.
     * Implements lazy loading: only fetches from API if not in cache.
     */
    public HistoricalData getHistoricalDataForCrypto(String id, String days) {
        if (id == null || id.isBlank())
            return new HistoricalData(null);
        if (days == null || days.isBlank())
            days = "1";

        // Check cache first - if found, return immediately (no API call)
        if (cache.hasHistoricalData(id, days)) {
            return cache.getHistoricalData(id, days);
        }

        // Not in cache - fetch from API (lazy loading)
        // This happens when user selects a time interval that wasn't preloaded
        System.out.println("Loading historical data for " + id + " (days=" + days + ") from API...");
        HistoricalData data = fetchHistoricalDataFromAPI(id, days);

        // Store in cache for future use
        if (data != null && data.getPoints() != null && !data.getPoints().isEmpty()) {
            cache.putHistoricalData(id, days, data);
            System.out.println("Successfully loaded and cached data for " + id + " (days=" + days + ")");
        } else {
            System.err.println("Failed to load data for " + id + " (days=" + days + ")");
        }

        return data;
    }

    private HistoricalData fetchHistoricalDataFromAPI(String id, String days) {
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        int maxRetries = 5; // Initial attempt + 4 retries
        // Retry delays: 10s, 20s, 30s, 30s (exponential backoff, then constant)
        int[] retryDelays = { 10000, 20000, 30000, 30000 }; // 10s, 20s, 30s, 30s

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String url = String.format("%s/coins/%s/market_chart?vs_currency=usd&days=%s", baseUrl, id, days);

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET();

                // Add API key header for Demo API
                String apiKey = props.getProperty("coingecko.api.key");
                if (apiKey != null && !apiKey.isBlank()) {
                    reqBuilder.header("x-cg-demo-api-key", apiKey);
                }

                HttpRequest request = reqBuilder.build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseMarketChartJson(response.body());
                } else {
                    // Any non-2xx status code (including 429 rate limit) - retry with increasing
                    // delay
                    if (attempt < maxRetries - 1) {
                        int delay = retryDelays[attempt];
                        String statusMsg = response.statusCode() == 429 ? "Rate limit exceeded"
                                : "API returned status " + response.statusCode();
                        System.out.println(statusMsg + " for " + id + ". Waiting " + (delay / 1000)
                                + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return new HistoricalData(null);
                        }
                        continue; // Retry the request
                    } else {
                        // All retries exhausted
                        System.err.println("Failed to fetch market_chart for " + id + " after " + maxRetries
                                + " attempts. Last status: " + response.statusCode());
                        return new HistoricalData(null);
                    }
                }
            } catch (IOException | InterruptedException e) {
                // Network errors or interruptions - retry with increasing delay
                if (attempt < maxRetries - 1) {
                    int delay = retryDelays[attempt];
                    System.out.println("Network error for " + id + " (" + e.getMessage() + "). Waiting "
                            + (delay / 1000) + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new HistoricalData(null);
                    }
                    continue; // Retry the request
                } else {
                    // All retries exhausted
                    System.err.println("Failed to fetch market_chart for " + id + " after " + maxRetries + " attempts: "
                            + e.getMessage());
                    return new HistoricalData(null);
                }
            }
        }

        return new HistoricalData(null);
    }

    /**
     * Callback interface for notifying when a crypto's data is loaded
     */
    public interface DataLoadedCallback {
        void onDataLoaded(String cryptoId, boolean success);

        void onIntervalDataLoaded(String cryptoId, String interval, boolean success);
    }

    private DataLoadedCallback dataLoadedCallback;

    // Track failed data loads for retry
    private final java.util.List<FailedDataLoad> failedLoads = new java.util.ArrayList<>();

    // Track interval loading progress: map of interval -> count of loaded cryptos
    private final java.util.Map<String, Integer> intervalLoadCounts = new java.util.HashMap<>();
    private int totalCryptoCount = 0;

    private static class FailedDataLoad {
        final String cryptoId;
        final String days;
        final String intervalName;

        FailedDataLoad(String cryptoId, String days, String intervalName) {
            this.cryptoId = cryptoId;
            this.days = days;
            this.intervalName = intervalName;
        }
    }

    /**
     * Set callback to be notified when crypto data is loaded
     */
    public void setDataLoadedCallback(DataLoadedCallback callback) {
        this.dataLoadedCallback = callback;
    }

    /**
     * Retry loading failed data
     */
    public void retryFailedLoads() {
        if (failedLoads.isEmpty()) {
            System.out.println("No failed loads to retry.");
            return;
        }

        System.out.println("Retrying " + failedLoads.size() + " failed data loads...");
        int delayBetweenCalls = 1000; // 1 seconds

        java.util.List<FailedDataLoad> toRetry = new java.util.ArrayList<>(failedLoads);
        failedLoads.clear();

        for (FailedDataLoad failed : toRetry) {
            try {
                System.out.println("Retrying " + failed.intervalName + " data for " + failed.cryptoId + "...");
                HistoricalData data = fetchHistoricalDataFromAPI(failed.cryptoId, failed.days);
                boolean success = false;
                if (data != null && data.getPoints() != null && !data.getPoints().isEmpty()) {
                    cache.putHistoricalData(failed.cryptoId, failed.days, data);
                    System.out.println("✓ Successfully loaded " + failed.intervalName + " data for " + failed.cryptoId);
                    success = true;
                    // Increment load count for this interval
                    intervalLoadCounts.put(failed.intervalName,
                            intervalLoadCounts.getOrDefault(failed.intervalName, 0) + 1);
                } else {
                    System.err
                            .println("✗ Still failed to load " + failed.intervalName + " data for " + failed.cryptoId);
                    // Add back to failed list
                    failedLoads.add(failed);
                }

                // Notify callback only if all cryptos have loaded this interval
                if (dataLoadedCallback != null && success) {
                    String interval = convertDaysToInterval(failed.days);
                    int loadedCount = intervalLoadCounts.getOrDefault(failed.intervalName, 0);
                    // Enable interval button only when all cryptos have loaded this interval
                    if (loadedCount == totalCryptoCount) {
                        dataLoadedCallback.onIntervalDataLoaded(null, failed.intervalName, true); // null cryptoId means
                                                                                                  // all loaded
                    }
                }

                // Wait between calls
                if (toRetry.indexOf(failed) < toRetry.size() - 1) {
                    Thread.sleep(delayBetweenCalls);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while retrying data for " + failed.cryptoId);
                break;
            } catch (Exception e) {
                System.err.println("Error retrying " + failed.intervalName + " data for " + failed.cryptoId + ": "
                        + e.getMessage());
                // Add back to failed list
                failedLoads.add(failed);
            }
        }

        System.out.println("Retry complete. " + failedLoads.size() + " loads still failed.");
    }

    private String convertDaysToInterval(String days) {
        return switch (days) {
            case "1" -> "1D";
            case "7" -> "1W";
            case "30" -> "1M";
            case "90" -> "3M";
            case "365" -> "1Y";
            default -> "1D";
        };
    }

    /**
     * Preload cryptocurrency data at startup using parallel API calls.
     * First attempts all 25 calls in parallel, then retries failures in batches.
     * 
     * With API key: Higher rate limits allow parallel calls
     * Strategy: Fire all 25 calls at once, retry failures in batches of 5
     */
    public void preloadAllData() {
        System.out.println("Preloading cryptocurrency data (parallel mode)...");

        // Fetch top cryptos (1 API call)
        List<Crypto> cryptos = getTopCryptos();
        totalCryptoCount = cryptos.size();
        System.out.println("Loaded " + cryptos.size() + " cryptocurrencies");

        // Initialize interval load counts
        intervalLoadCounts.clear();
        String[] intervalNames = { "1D", "1W", "1M", "3M", "1Y" };
        for (String name : intervalNames) {
            intervalLoadCounts.put(name, 0);
        }

        // Time intervals to load
        String[] intervals = { "1", "7", "30", "90", "365" }; // 1D, 1W, 1M, 3M, 1Y

        // Create all fetch tasks (5 cryptos × 5 intervals = 25 calls)
        List<FetchTask> allTasks = new ArrayList<>();
        for (Crypto crypto : cryptos) {
            for (int i = 0; i < intervals.length; i++) {
                allTasks.add(new FetchTask(crypto.getId(), crypto.getName(), intervals[i], intervalNames[i]));
            }
        }

        System.out.println("Starting " + allTasks.size() + " parallel API calls...");
        long startTime = System.currentTimeMillis();

        // Execute all calls in parallel
        List<CompletableFuture<FetchResult>> futures = new ArrayList<>();
        for (FetchTask task : allTasks) {
            CompletableFuture<FetchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    HistoricalData data = fetchHistoricalDataFromAPINoRetry(task.cryptoId, task.days);
                    boolean success = data != null && data.getPoints() != null && !data.getPoints().isEmpty();
                    return new FetchResult(task, data, success);
                } catch (Exception e) {
                    System.err.println("Error fetching " + task.intervalName + " for " + task.cryptoName + ": " + e.getMessage());
                    return new FetchResult(task, null, false);
                }
            });
            futures.add(future);
        }

        // Wait for all parallel calls to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Process results
        List<FetchTask> failedTasks = new ArrayList<>();
        ConcurrentHashMap<String, AtomicInteger> intervalSuccessCounts = new ConcurrentHashMap<>();
        for (String name : intervalNames) {
            intervalSuccessCounts.put(name, new AtomicInteger(0));
        }

        for (CompletableFuture<FetchResult> future : futures) {
            try {
                FetchResult result = future.get();
                if (result.success) {
                    cache.putHistoricalData(result.task.cryptoId, result.task.days, result.data);
                    System.out.println("✓ " + result.task.intervalName + " for " + result.task.cryptoName);
                    intervalSuccessCounts.get(result.task.intervalName).incrementAndGet();
                    
                    // Notify callback for 1D data (enables crypto in sidebar)
                    if (result.task.days.equals("1") && dataLoadedCallback != null) {
                        final String cryptoId = result.task.cryptoId;
                        javafx.application.Platform.runLater(() -> {
                            dataLoadedCallback.onDataLoaded(cryptoId, true);
                        });
                    }
                } else {
                    System.err.println("✗ " + result.task.intervalName + " for " + result.task.cryptoName + " - will retry");
                    failedTasks.add(result.task);
                }
            } catch (Exception e) {
                System.err.println("Error processing result: " + e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Parallel phase complete in " + elapsed + "ms. Success: " + (allTasks.size() - failedTasks.size()) + "/" + allTasks.size());

        // Update interval load counts and notify callbacks
        for (String intervalName : intervalNames) {
            int count = intervalSuccessCounts.get(intervalName).get();
            intervalLoadCounts.put(intervalName, count);
            if (count == totalCryptoCount && dataLoadedCallback != null) {
                final String interval = intervalName;
                javafx.application.Platform.runLater(() -> {
                    dataLoadedCallback.onIntervalDataLoaded(null, interval, true);
                });
            }
        }

        // Retry failed tasks in batches
        if (!failedTasks.isEmpty()) {
            System.out.println("Retrying " + failedTasks.size() + " failed calls in batches...");
            retryInBatches(failedTasks, intervalSuccessCounts, 5, 2000); // batch size 5, 2s between batches
        }

        System.out.println("All data preloading complete!");
    }

    /**
     * Retry failed tasks in batches with delay between batches
     */
    private void retryInBatches(List<FetchTask> failedTasks, ConcurrentHashMap<String, AtomicInteger> intervalSuccessCounts, 
                                 int batchSize, int delayBetweenBatchesMs) {
        List<FetchTask> remaining = new ArrayList<>(failedTasks);
        int retryAttempt = 0;
        int maxRetryAttempts = 3;

        while (!remaining.isEmpty() && retryAttempt < maxRetryAttempts) {
            retryAttempt++;
            System.out.println("Retry attempt " + retryAttempt + "/" + maxRetryAttempts + " for " + remaining.size() + " tasks...");
            
            List<FetchTask> stillFailed = new ArrayList<>();
            
            // Process in batches
            for (int i = 0; i < remaining.size(); i += batchSize) {
                int end = Math.min(i + batchSize, remaining.size());
                List<FetchTask> batch = remaining.subList(i, end);
                
                System.out.println("Processing batch of " + batch.size() + " calls...");
                
                // Execute batch in parallel
                List<CompletableFuture<FetchResult>> batchFutures = new ArrayList<>();
                for (FetchTask task : batch) {
                    CompletableFuture<FetchResult> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            HistoricalData data = fetchHistoricalDataFromAPINoRetry(task.cryptoId, task.days);
                            boolean success = data != null && data.getPoints() != null && !data.getPoints().isEmpty();
                            return new FetchResult(task, data, success);
                        } catch (Exception e) {
                            return new FetchResult(task, null, false);
                        }
                    });
                    batchFutures.add(future);
                }
                
                // Wait for batch to complete
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
                
                // Process batch results
                for (CompletableFuture<FetchResult> future : batchFutures) {
                    try {
                        FetchResult result = future.get();
                        if (result.success) {
                            cache.putHistoricalData(result.task.cryptoId, result.task.days, result.data);
                            System.out.println("✓ Retry success: " + result.task.intervalName + " for " + result.task.cryptoName);
                            intervalSuccessCounts.get(result.task.intervalName).incrementAndGet();
                            
                            // Notify callback for 1D data
                            if (result.task.days.equals("1") && dataLoadedCallback != null) {
                                final String cryptoId = result.task.cryptoId;
                                javafx.application.Platform.runLater(() -> {
                                    dataLoadedCallback.onDataLoaded(cryptoId, true);
                                });
                            }
                            
                            // Check if interval is now complete
                            int count = intervalSuccessCounts.get(result.task.intervalName).get();
                            intervalLoadCounts.put(result.task.intervalName, count);
                            if (count == totalCryptoCount && dataLoadedCallback != null) {
                                final String interval = result.task.intervalName;
                                javafx.application.Platform.runLater(() -> {
                                    dataLoadedCallback.onIntervalDataLoaded(null, interval, true);
                                });
                            }
                        } else {
                            stillFailed.add(result.task);
                        }
                    } catch (Exception e) {
                        // Keep task in failed list
                    }
                }
                
                // Wait between batches (except for last batch)
                if (end < remaining.size()) {
                    try {
                        Thread.sleep(delayBetweenBatchesMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            
            remaining = stillFailed;
            
            // Wait before next retry attempt
            if (!remaining.isEmpty() && retryAttempt < maxRetryAttempts) {
                try {
                    System.out.println("Waiting 5s before next retry attempt...");
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        // Track any remaining failures
        for (FetchTask task : remaining) {
            failedLoads.add(new FailedDataLoad(task.cryptoId, task.days, task.intervalName));
        }
        
        if (!remaining.isEmpty()) {
            System.err.println(remaining.size() + " tasks still failed after all retries.");
        }
    }

    /**
     * Fetch historical data without retry logic (for parallel calls)
     */
    private HistoricalData fetchHistoricalDataFromAPINoRetry(String id, String days) {
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        
        try {
            String url = String.format("%s/coins/%s/market_chart?vs_currency=usd&days=%s", baseUrl, id, days);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();

            // Add API key header for Demo API
            String apiKey = props.getProperty("coingecko.api.key");
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("x-cg-demo-api-key", apiKey);
            }

            HttpRequest request = reqBuilder.build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseMarketChartJson(response.body());
            } else {
                System.err.println("API returned " + response.statusCode() + " for " + id + " days=" + days);
                return null;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error fetching " + id + " days=" + days + ": " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    // Helper classes for parallel fetch
    private static class FetchTask {
        final String cryptoId;
        final String cryptoName;
        final String days;
        final String intervalName;

        FetchTask(String cryptoId, String cryptoName, String days, String intervalName) {
            this.cryptoId = cryptoId;
            this.cryptoName = cryptoName;
            this.days = days;
            this.intervalName = intervalName;
        }
    }

    private static class FetchResult {
        final FetchTask task;
        final HistoricalData data;
        final boolean success;

        FetchResult(FetchTask task, HistoricalData data, boolean success) {
            this.task = task;
            this.data = data;
            this.success = success;
        }
    }

    /**
     * Get count of failed data loads
     */
    public int getFailedLoadsCount() {
        return failedLoads.size();
    }

    /**
     * Clear cache to allow refreshing all data
     */
    public void clearCache() {
        cache.clear();
        failedLoads.clear();
    }

    private Properties loadProperties() {
        Properties p = new Properties();

        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                p.load(is);
            }
        } catch (IOException e) {
            System.err.println("Failed to load properties from " + path + ": " + e.getMessage());
        }
        return p;
    }

    /**
     * Parse a market_chart JSON response into HistoricalData using prices and
     * total_volumes.
     * Expected arrays of [ [timestampMs, value], ... ]. If volumes array is
     * missing, volume will be null.
     */
    private HistoricalData parseMarketChartJson(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode prices = root.path("prices");
        JsonNode volumes = root.path("total_volumes");

        if (!prices.isArray()) {
            return new HistoricalData(null);
        }

        List<ChartPoint> points = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            JsonNode pNode = prices.get(i);
            if (!pNode.isArray() || pNode.size() < 2)
                continue;

            long ts = pNode.get(0).asLong(0L);
            Double price = pNode.get(1).isNull() ? null : pNode.get(1).asDouble();

            Double vol = null;
            if (volumes.isArray() && i < volumes.size()) {
                JsonNode vNode = volumes.get(i);
                if (vNode.isArray() && vNode.size() >= 2) {
                    vol = vNode.get(1).isNull() ? null : vNode.get(1).asDouble();
                }
            }

            try {
                ChartPoint cp = new ChartPoint(Instant.ofEpochMilli(ts), price, vol);
                points.add(cp);
            } catch (Exception ex) {
                // skip malformed timestamps
            }
        }

        return new HistoricalData(points);
    }

    private static String formatMoneyShort(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0)
            return "$0";
        double abs = Math.abs(value);
        DecimalFormat df = new DecimalFormat("#,##0.##");
        if (abs >= 1_000_000_000_000.0) {
            return "$" + df.format(value / 1_000_000_000_000.0) + "T";
        } else if (abs >= 1_000_000_000.0) {
            return "$" + df.format(value / 1_000_000_000.0) + "B";
        } else if (abs >= 1_000_000.0) {
            return "$" + df.format(value / 1_000_000.0) + "M";
        } else if (abs >= 1_000.0) {
            return "$" + df.format(value / 1_000.0) + "K";
        } else {
            return "$" + df.format(value);
        }
    }

    private static String formatNumberShort(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0)
            return "0";
        double abs = Math.abs(value);
        DecimalFormat df = new DecimalFormat("#,##0.##");
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

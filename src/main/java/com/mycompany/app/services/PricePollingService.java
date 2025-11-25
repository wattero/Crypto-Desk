package com.mycompany.app.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.app.models.Crypto;

/**
 * Service for polling current prices of top cryptocurrencies.
 * Uses the CoinGecko /simple/price endpoint which is lightweight
 * and can fetch multiple prices in a single call.
 * 
 * This service is separate from CryptoService to:
 * 1. Keep polling logic isolated from data fetching
 * 2. Manage rate limiting independently
 * 3. Avoid interfering with historical data caching
 */
public class PricePollingService {
    private static final String DEFAULT_API_URL = "https://api.coingecko.com/api/v3";
    private static final String PROPERTIES_PATH = "/application.properties";
    
    // Poll every 30 seconds - CoinGecko free tier allows 10-15 calls/min
    // We use 30s to be very conservative and avoid rate limiting
    // since other parts of the app also make API calls
    private static final long POLLING_INTERVAL_SECONDS = 5;
    
    // Initial delay before first poll - wait for initial data to load
    private static final long INITIAL_DELAY_SECONDS = 30;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Properties props = loadProperties();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "price-polling-thread");
        t.setDaemon(true); // Allow JVM to exit even if this thread is running
        return t;
    });
    
    // Current prices map: cryptoId -> price
    private final Map<String, Double> currentPrices = new ConcurrentHashMap<>();
    
    // Current 24h change percentages: cryptoId -> changePercent
    private final Map<String, Double> currentChanges = new ConcurrentHashMap<>();
    
    // List of crypto IDs to poll
    private List<String> cryptoIds;
    
    // Callback for when prices are updated
    private PriceUpdateCallback priceUpdateCallback;
    
    // Track if polling is active
    private volatile boolean isPolling = false;
    
    // Track consecutive failures for backoff
    private int consecutiveFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    
    /**
     * Callback interface for price updates
     */
    public interface PriceUpdateCallback {
        /**
         * Called when prices have been updated
         * @param prices Map of cryptoId -> current price
         * @param changes Map of cryptoId -> 24h change percentage
         */
        void onPricesUpdated(Map<String, Double> prices, Map<String, Double> changes);
    }
    
    /**
     * Set the callback to be notified when prices are updated
     */
    public void setPriceUpdateCallback(PriceUpdateCallback callback) {
        this.priceUpdateCallback = callback;
    }
    
    /**
     * Start polling for price updates.
     * Uses the provided list of cryptos to determine which IDs to poll.
     * 
     * @param cryptos List of cryptocurrencies to poll prices for
     */
    public void startPolling(List<Crypto> cryptos) {
        if (isPolling) {
            System.out.println("Price polling already active");
            return;
        }
        
        if (cryptos == null || cryptos.isEmpty()) {
            System.out.println("No cryptos provided for polling");
            return;
        }
        
        // Store crypto IDs and initialize with current prices
        this.cryptoIds = cryptos.stream()
                .map(Crypto::getId)
                .collect(Collectors.toList());
        
        // Initialize current prices from the provided data
        for (Crypto crypto : cryptos) {
            currentPrices.put(crypto.getId(), crypto.getPrice());
            currentChanges.put(crypto.getId(), crypto.getChangePercent());
        }
        
        isPolling = true;
        consecutiveFailures = 0;
        
        System.out.println("Starting price polling for " + cryptoIds.size() + " cryptos with " 
                + POLLING_INTERVAL_SECONDS + "s interval (first poll in " + INITIAL_DELAY_SECONDS + "s)");
        
        // Schedule periodic polling
        scheduler.scheduleAtFixedRate(
                this::pollPrices,
                INITIAL_DELAY_SECONDS,
                POLLING_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }
    
    /**
     * Stop polling for price updates
     */
    public void stopPolling() {
        isPolling = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Price polling stopped");
    }
    
    /**
     * Get the current price for a crypto
     * @param cryptoId The crypto ID (e.g., "bitcoin")
     * @return The current price, or null if not available
     */
    public Double getCurrentPrice(String cryptoId) {
        return currentPrices.get(cryptoId);
    }
    
    /**
     * Get the current 24h change percentage for a crypto
     * @param cryptoId The crypto ID (e.g., "bitcoin")
     * @return The 24h change percentage, or null if not available
     */
    public Double getCurrentChange(String cryptoId) {
        return currentChanges.get(cryptoId);
    }
    
    /**
     * Get all current prices
     * @return Map of cryptoId -> price
     */
    public Map<String, Double> getAllCurrentPrices() {
        return new ConcurrentHashMap<>(currentPrices);
    }
    
    /**
     * Get all current 24h changes
     * @return Map of cryptoId -> changePercent
     */
    public Map<String, Double> getAllCurrentChanges() {
        return new ConcurrentHashMap<>(currentChanges);
    }
    
    /**
     * Poll prices from the API
     */
    private void pollPrices() {
        if (!isPolling || cryptoIds == null || cryptoIds.isEmpty()) {
            return;
        }
        
        // If we've had too many consecutive failures, skip this poll cycle
        // This implements exponential backoff
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            System.out.println("Skipping price poll due to " + consecutiveFailures + " consecutive failures. Will retry next cycle.");
            consecutiveFailures--; // Slowly recover
            return;
        }
        
        try {
            String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
            
            // Build comma-separated list of IDs
            String ids = String.join(",", cryptoIds);
            
            // Use /simple/price endpoint - lightweight and supports multiple coins
            // include_24hr_change=true to get the change percentage as well
            String url = String.format(
                    "%s/simple/price?ids=%s&vs_currencies=usd&include_24hr_change=true",
                    baseUrl, ids);
            
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
                parsePriceResponse(response.body());
                consecutiveFailures = 0; // Reset on success
                
                // Notify callback
                if (priceUpdateCallback != null) {
                    priceUpdateCallback.onPricesUpdated(
                            new ConcurrentHashMap<>(currentPrices),
                            new ConcurrentHashMap<>(currentChanges)
                    );
                }
                
                System.out.println("Price poll successful - updated " + currentPrices.size() + " prices");
            } else if (response.statusCode() == 429) {
                // Rate limited - increment failure counter
                consecutiveFailures++;
                System.out.println("Price poll rate limited (429). Consecutive failures: " + consecutiveFailures);
            } else {
                consecutiveFailures++;
                System.err.println("Price poll failed with status: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            consecutiveFailures++;
            System.err.println("Price poll error: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Parse the /simple/price response
     * Format: { "bitcoin": { "usd": 12345.67, "usd_24h_change": 1.23 }, ... }
     */
    private void parsePriceResponse(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        
        for (String cryptoId : cryptoIds) {
            JsonNode coinNode = root.path(cryptoId);
            if (!coinNode.isMissingNode()) {
                double price = coinNode.path("usd").asDouble(0.0);
                double change = coinNode.path("usd_24h_change").asDouble(0.0);
                
                if (price > 0) {
                    currentPrices.put(cryptoId, price);
                    currentChanges.put(cryptoId, change);
                }
            }
        }
    }
    
    private Properties loadProperties() {
        Properties p = new Properties();
        try (InputStream is = getClass().getResourceAsStream(PROPERTIES_PATH)) {
            if (is != null) {
                p.load(is);
            }
        } catch (IOException e) {
            System.err.println("Failed to load properties: " + e.getMessage());
        }
        return p;
    }
}

package com.deac.features.payment.service.paypal;

import com.deac.exception.MyException;
import com.deac.features.payment.dto.CheckoutItemDto;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class PaypalPaymentService {

    private final Base64.Encoder encoder;

    private final HttpClient client;

    private final Gson gson;

    private final String baseUrl;

    private final String secret;

    private final String clientId;

    private final String currency;

    @Autowired
    public PaypalPaymentService(Environment environment) {
        this.encoder = Base64.getEncoder();
        this.client = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.baseUrl = Objects.requireNonNull(environment.getProperty("paypal.baseurl", String.class));
        this.secret = Objects.requireNonNull(environment.getProperty("paypal.secret", String.class));
        this.clientId = Objects.requireNonNull(environment.getProperty("paypal.clientid", String.class));
        this.currency = Objects.requireNonNull(environment.getProperty("membership.currency", String.class));
    }

    public Object createOrder(List<CheckoutItemDto> items) {
        try {
            String accessToken = generateAccessToken();
            List<Map<String, Object>> itemsList = new ArrayList<>();
            long totalAmount = 0;
            for (CheckoutItemDto item : items) {
                Map<String, Object> tmp = new HashMap<>();
                tmp.put("name", item.getMonthlyTransactionReceiptMonth().toString());
                tmp.put("description", "Monthly membership fee for " + item.getMonthlyTransactionReceiptMonth().toString());
                tmp.put("quantity", 1);
                tmp.put("unit_amount", Map.of(
                        "currency_code", currency.toUpperCase(),
                        "value", item.getAmount()
                ));
                itemsList.add(tmp);
                totalAmount += item.getAmount();
            }
            Map<String, Object> body = new HashMap<>();
            body.put("intent", "CAPTURE");
            body.put("purchase_units", List.of(
                    Map.of(
                            "items", itemsList,
                            "amount", Map.of(
                                    "currency_code", currency.toUpperCase(),
                                    "value", totalAmount,
                                    "breakdown", Map.of(
                                            "item_total", Map.of(
                                                    "currency_code", currency.toUpperCase(),
                                                    "value", totalAmount
                                            )
                                    )
                            )
                    )
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/v2/checkout/orders", baseUrl)))
                    .method("POST", HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .header("Authorization", String.format("Bearer %s", accessToken))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            evaluateResponse(response);
            return gson.fromJson(response.body(), Object.class);
        } catch (IOException | InterruptedException e) {
            throw new MyException("Unsuccessful payment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String generateAccessToken() throws IOException, InterruptedException {
        String tmp = clientId + ":" + secret;
        String authToken = encoder.encodeToString(tmp.getBytes());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/v1/oauth2/token", baseUrl)))
                .method("POST", HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .header("Authorization", String.format("Basic %s", authToken))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        evaluateResponse(response);
        TypeToken<Map<String, String>> mapType = new TypeToken<>() {
        };
        Map<String, String> result = gson.fromJson(response.body(), mapType.getType());
        return result.get("access_token");
    }

    private void evaluateResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new MyException("Unsuccessful payment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Object confirmOrder(String orderId) {
        try {
            String accessToken = generateAccessToken();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/v2/checkout/orders/%s/capture", baseUrl, orderId)))
                    .method("POST", HttpRequest.BodyPublishers.noBody())
                    .header("Authorization", String.format("Bearer %s", accessToken))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            evaluateResponse(response);
            return gson.fromJson(response.body(), Object.class);
        } catch (IOException | InterruptedException e) {
            throw new MyException("Unsuccessful payment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String savePayment() {
        return null;
    }

}

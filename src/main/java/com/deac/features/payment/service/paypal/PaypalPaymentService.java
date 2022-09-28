package com.deac.features.payment.service.paypal;

import com.deac.exception.MyException;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.payment.dto.CheckoutItemDto;
import com.deac.features.payment.persistence.entity.MonthlyTransaction;
import com.deac.features.payment.service.general.PaymentService;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PaypalPaymentService {

    private final UserService userService;

    private final PaymentService paymentService;

    private final Base64.Encoder encoder;

    private final HttpClient client;

    private final Gson gson;

    private final String baseUrl;

    private final String secret;

    private final String clientId;

    private final String currency;

    @Autowired
    public PaypalPaymentService(UserService userService, PaymentService paymentService, Environment environment) {
        this.userService = userService;
        this.paymentService = paymentService;
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

    public String savePayment(String orderId) {
        User currentUser = userService.getCurrentUser();
        MembershipEntry currentUserMembershipEntry = currentUser.getMembershipEntry();
        Map<String, MonthlyTransaction> monthlyTransactions = currentUserMembershipEntry.getMonthlyTransactions();
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM");
            if (!monthlyTransactions.containsKey(YearMonth.now().format(formatter))) {
                monthlyTransactions.put(YearMonth.now().format(formatter), new MonthlyTransaction(YearMonth.now(), null));
            }
            JSONObject order = retrieveOrder(orderId);
            JSONObject purchaseUnit = order.getJSONArray("purchase_units").getJSONObject(0);
            Long totalAmount = Double.valueOf(purchaseUnit.getJSONObject("amount").get("value").toString()).longValue() * 100;
            Comparator<String> comparator = Comparator.comparing(YearMonth::parse);
            SortedMap<String, String> items = new TreeMap<>(comparator.reversed());
            JSONArray purchaseUnitItems = purchaseUnit.getJSONArray("items");
            for (int i = 0; i < purchaseUnitItems.length(); i++) {
                JSONObject tmp = purchaseUnitItems.getJSONObject(i);
                items.put(tmp.get("name").toString(), ((Long) Double.valueOf(tmp.getJSONObject("unit_amount").get("value").toString()).longValue()).toString());
            }
            String monthlyTransactionReceiptPath = paymentService.generatePaymentReceipt(order.get("id").toString(), "PayPal", totalAmount, items, currentUser);
            for (Map.Entry<String, String> itemEntry : items.entrySet()) {
                String yearMonth = YearMonth.parse(itemEntry.getKey()).format(formatter);
                monthlyTransactions.get(yearMonth).setMonthlyTransactionReceiptPath(monthlyTransactionReceiptPath);
            }
        } catch (Exception e) {
            throw new MyException("Could not save payment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        currentUser.setMembershipEntry(currentUserMembershipEntry);
        currentUserMembershipEntry.setHasPaidMembershipFee(true);
        currentUserMembershipEntry.setApproved(true);
        userService.saveUser(currentUser);
        return "Payment successfully saved";
    }

    private JSONObject retrieveOrder(String orderId) {
        try {
            String accessToken = generateAccessToken();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/v2/checkout/orders/%s", baseUrl, orderId)))
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .header("Authorization", String.format("Bearer %s", accessToken))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            evaluateResponse(response);
            return new JSONObject(response.body());
        } catch (IOException | InterruptedException e) {
            throw new MyException("Could not retrieve payment info", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}

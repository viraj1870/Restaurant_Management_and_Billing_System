package com.restaurant.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests following the project's testing plan. Uses the seeded demo restaurant. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RestaurantFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String merchant, kitchen, customer;
    long restaurantId;
    long paneerTikkaId, naanId;

    @BeforeEach
    void setUp() throws Exception {
        merchant = login("merchant@demo.com");
        kitchen = login("kitchen@demo.com");
        customer = login("customer@demo.com");
        JsonNode menu = read(mvc.perform(get("/api/menu")));
        restaurantId = menu.at("/restaurant/id").asLong();
        for (JsonNode cat : menu.get("categories")) {
            for (JsonNode item : cat.get("items")) {
                if (item.get("name").asText().equals("Paneer Tikka")) paneerTikkaId = item.get("id").asLong();
                if (item.get("name").asText().equals("Butter Naan")) naanId = item.get("id").asLong();
            }
        }
    }

    // ---------- Authentication ----------

    @Test
    void duplicateEmailAndMobileAreRejected() throws Exception {
        post("/api/auth/customer/signup", null, Map.of("name", "A", "email", "customer@demo.com",
                "mobile", "9123456780", "password", "Passw0rd1"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Email is already registered"));
        post("/api/auth/customer/signup", null, Map.of("name", "A", "email", "new@demo.com",
                "mobile", "9876500003", "password", "Passw0rd1"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Mobile number is already registered"));
    }

    @Test
    void signupValidatesInputAndHashesPassword() throws Exception {
        post("/api/auth/customer/signup", null, Map.of("name", "A", "email", "bad", "mobile", "123", "password", "short"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());

        JsonNode res = read(post("/api/auth/merchant/signup", null, Map.of("name", "Owner", "email", "owner2@x.com",
                "mobile", "9000000011", "password", "Passw0rd1", "restaurantName", "Cafe Two")).andExpect(status().isCreated()));
        assertThat(res.at("/user/role").asText()).isEqualTo("MERCHANT");
        assertThat(res.at("/user/restaurantName").asText()).isEqualTo("Cafe Two");
        assertThat(res.toString()).doesNotContain("Passw0rd1").doesNotContain("passwordHash");
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        post("/api/auth/signin", null, Map.of("login", "customer@demo.com", "password", "wrong"))
                .andExpect(status().isUnauthorized());
        post("/api/auth/signin", null, Map.of("login", "nobody@demo.com", "password", "Demo@123"))
                .andExpect(status().isUnauthorized());
        // login by mobile number works
        post("/api/auth/signin", null, Map.of("login", "9876500003", "password", "Demo@123")).andExpect(status().isOk());
    }

    @Test
    void roleBasedAccessIsEnforced() throws Exception {
        mvc.perform(get("/api/merchant/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/merchant/orders").header("Authorization", bearer(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/daily").header("Authorization", bearer(kitchen))).andExpect(status().isForbidden());
        mvc.perform(get("/api/kitchen/orders").header("Authorization", bearer(kitchen))).andExpect(status().isOk());
        post("/api/orders", merchant, orderBody(null, 0)).andExpect(status().isForbidden());
    }

    @Test
    void merchantCannotAccessAnotherRestaurantsData() throws Exception {
        JsonNode order = read(post("/api/orders", customer, orderBody(null, 0)).andExpect(status().isCreated()));
        String other = read(post("/api/auth/merchant/signup", null, Map.of("name", "Other", "email", "other@x.com",
                "mobile", "9000000022", "password", "Passw0rd1", "restaurantName", "Other Place"))).get("token").asText();
        long orderId = order.get("id").asLong();
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        put("/api/orders/" + orderId + "/status", other, Map.of("status", "PREPARING")).andExpect(status().isNotFound());
        put("/api/merchant/menu-items/" + paneerTikkaId, other, Map.of("categoryId", 1, "name", "Hack", "price", 1))
                .andExpect(status().isNotFound());
    }

    // ---------- Orders & billing ----------

    @Test
    void billIsCalculatedFromServerPricesWithTax() throws Exception {
        // 2 x Paneer Tikka (220) + 3 x Butter Naan (50) = 590; 5% GST = 29.50; total 619.50
        JsonNode order = read(post("/api/orders", customer, orderBody(null, 0)).andExpect(status().isCreated()));
        assertThat(order.get("subtotal").decimalValue()).isEqualByComparingTo("590.00");
        assertThat(order.get("taxAmount").decimalValue()).isEqualByComparingTo("29.50");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("619.50");
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(order.get("paymentStatus").asText()).isEqualTo("UNPAID");
    }

    @Test
    void couponAndLoyaltyDiscountsAreApplied() throws Exception {
        // WELCOME10 = 10% capped at 100 -> 59; 50 points -> 50; taxable 481; tax 24.05; total 505.05
        JsonNode order = read(post("/api/orders", customer, orderBody("welcome10", 50)).andExpect(status().isCreated()));
        assertThat(order.get("couponDiscount").decimalValue()).isEqualByComparingTo("59.00");
        assertThat(order.get("loyaltyDiscount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("505.05");
        JsonNode me = read(mvc.perform(get("/api/users/me").header("Authorization", bearer(customer))));
        assertThat(me.get("loyaltyPoints").asInt()).isEqualTo(50);

        // WELCOME10 has a per-customer limit of 1
        post("/api/orders", customer, orderBody("WELCOME10", 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You have already used this coupon the maximum number of times"));
    }

    @Test
    void invalidOrExpiredCouponsAreRejected() throws Exception {
        post("/api/orders", customer, orderBody("OLD20", 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This coupon has expired"));
        post("/api/orders", customer, orderBody("NOPE", 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid coupon code"));
        // FLAT50 requires 500 minimum; a single naan is below it
        Map<String, Object> small = Map.of("restaurantId", restaurantId, "couponCode", "FLAT50",
                "items", List.of(Map.of("menuItemId", naanId, "quantity", 1)));
        post("/api/orders", customer, small).andExpect(status().isBadRequest());
        // preview reports the reason without failing
        JsonNode preview = read(post("/api/orders/preview", customer, orderBody("OLD20", 0)).andExpect(status().isOk()));
        assertThat(preview.get("couponMessage").asText()).isEqualTo("This coupon has expired");
        assertThat(preview.get("couponDiscount").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unavailableItemsCannotBeOrdered() throws Exception {
        patch("/api/merchant/menu-items/" + naanId + "/availability", merchant, Map.of("available", false)).andExpect(status().isOk());
        post("/api/orders", customer, orderBody(null, 0)).andExpect(status().isBadRequest());
    }

    // ---------- Kitchen, payment, completion, loyalty, feedback, reports ----------

    @Test
    void fullOrderLifecycle() throws Exception {
        long id = read(post("/api/orders", customer, orderBody(null, 0))).get("id").asLong();

        // kitchen sees it and moves through Preparing -> Ready; kitchen cannot complete
        JsonNode board = read(mvc.perform(get("/api/kitchen/orders").header("Authorization", bearer(kitchen))));
        assertThat(board.findValuesAsText("id")).contains(String.valueOf(id));
        put("/api/orders/" + id + "/status", kitchen, Map.of("status", "READY")).andExpect(status().isBadRequest());
        put("/api/orders/" + id + "/status", kitchen, Map.of("status", "PREPARING")).andExpect(status().isOk());
        post("/api/orders/" + id + "/cancel", customer, null).andExpect(status().isBadRequest());
        put("/api/orders/" + id + "/status", kitchen, Map.of("status", "READY")).andExpect(status().isOk());
        put("/api/orders/" + id + "/status", kitchen, Map.of("status", "COMPLETED")).andExpect(status().isForbidden());

        // cannot complete before payment
        put("/api/orders/" + id + "/status", merchant, Map.of("status", "COMPLETED")).andExpect(status().isBadRequest());

        // payment failure then success
        JsonNode failed = read(post("/api/orders/" + id + "/payments", customer, Map.of("method", "CARD", "cardNumber", "4000000000000002")));
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        JsonNode ok = read(post("/api/orders/" + id + "/payments", customer, Map.of("method", "UPI", "upiId", "priya@okaxis")));
        assertThat(ok.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(ok.get("amount").decimalValue()).isEqualByComparingTo("619.50");
        post("/api/orders/" + id + "/payments", customer, Map.of("method", "UPI", "upiId", "priya@okaxis"))
                .andExpect(status().isConflict());

        // feedback not allowed before completion
        post("/api/feedback", customer, Map.of("orderId", id, "rating", 5)).andExpect(status().isBadRequest());

        JsonNode done = read(put("/api/orders/" + id + "/status", merchant, Map.of("status", "COMPLETED")).andExpect(status().isOk()));
        assertThat(done.get("loyaltyPointsEarned").asInt()).isEqualTo(6); // floor(619.50 / 100)

        post("/api/feedback", customer, Map.of("orderId", id, "rating", 4, "comment", "Tasty")).andExpect(status().isCreated());
        post("/api/feedback", customer, Map.of("orderId", id, "rating", 5)).andExpect(status().isConflict());
        post("/api/feedback", customer, Map.of("orderId", id, "type", "COMPLAINT", "comment", "Naan was cold")).andExpect(status().isCreated());

        // receipt & notifications
        JsonNode receipt = read(mvc.perform(get("/api/orders/" + id + "/receipt").header("Authorization", bearer(customer))));
        assertThat(receipt.at("/restaurant/gstNumber").asText()).isNotBlank();
        assertThat(receipt.get("payments")).hasSize(2);
        JsonNode notes = read(mvc.perform(get("/api/customer/notifications").header("Authorization", bearer(customer))));
        assertThat(notes.size()).isGreaterThanOrEqualTo(4);

        // reports
        JsonNode daily = read(mvc.perform(get("/api/reports/daily").header("Authorization", bearer(merchant))));
        assertThat(daily.get("totalOrders").asInt()).isEqualTo(1);
        assertThat(daily.get("completedOrders").asInt()).isEqualTo(1);
        assertThat(daily.get("amountCollected").decimalValue()).isEqualByComparingTo("619.50");
        assertThat(daily.at("/popularItems/0/name").asText()).isEqualTo("Butter Naan");
        JsonNode fb = read(mvc.perform(get("/api/reports/feedback").header("Authorization", bearer(merchant))));
        assertThat(fb.get("averageRating").asDouble()).isEqualTo(4.0);
        assertThat(fb.get("openComplaints").asInt()).isEqualTo(1);
        JsonNode customers = read(mvc.perform(get("/api/merchant/customers").header("Authorization", bearer(merchant))));
        assertThat(customers.get(0).get("totalOrders").asInt()).isEqualTo(1);
    }

    @Test
    void cancellationRestoresLoyaltyPoints() throws Exception {
        long id = read(post("/api/orders", customer, orderBody(null, 40))).get("id").asLong();
        assertThat(points()).isEqualTo(60);
        post("/api/orders/" + id + "/cancel", customer, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(points()).isEqualTo(100);
        post("/api/orders/" + id + "/payments", customer, Map.of("method", "UPI", "upiId", "a@ok")).andExpect(status().isBadRequest());
    }

    @Test
    void counterCashPaymentFlow() throws Exception {
        long id = read(post("/api/orders", customer, orderBody(null, 0))).get("id").asLong();
        JsonNode pending = read(post("/api/orders/" + id + "/payments", customer, Map.of("method", "CASH")));
        assertThat(pending.get("status").asText()).isEqualTo("PENDING");
        JsonNode settled = read(post("/api/orders/" + id + "/payments", merchant, Map.of("method", "CASH")));
        assertThat(settled.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(settled.get("id").asLong()).isEqualTo(pending.get("id").asLong());
        JsonNode order = read(mvc.perform(get("/api/orders/" + id).header("Authorization", bearer(customer))));
        assertThat(order.get("paymentStatus").asText()).isEqualTo("PAID");
    }

    @Test
    void onlinePaymentSupersedesPendingCounterRequest() throws Exception {
        long id = read(post("/api/orders", customer, orderBody(null, 0))).get("id").asLong();
        post("/api/orders/" + id + "/payments", customer, Map.of("method", "CASH")).andExpect(status().isOk());
        post("/api/orders/" + id + "/payments", customer, Map.of("method", "UPI", "upiId", "priya@okaxis"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        JsonNode payments = read(mvc.perform(get("/api/orders/" + id + "/payments").header("Authorization", bearer(customer))));
        assertThat(payments.findValuesAsText("status")).containsExactlyInAnyOrder("SUCCESS", "FAILED");
    }

    @Test
    void merchantManagesMenuAndCoupons() throws Exception {
        JsonNode cat = read(post("/api/merchant/categories", merchant, Map.of("name", "Chinese")).andExpect(status().isCreated()));
        post("/api/merchant/categories", merchant, Map.of("name", "chinese")).andExpect(status().isConflict());
        JsonNode item = read(post("/api/merchant/menu-items", merchant, Map.of("categoryId", cat.get("id").asLong(),
                "name", "Hakka Noodles", "price", 180, "vegetarian", true)).andExpect(status().isCreated()));
        post("/api/merchant/menu-items", merchant, Map.of("categoryId", cat.get("id").asLong(), "name", "Bad", "price", -5))
                .andExpect(status().isBadRequest());
        JsonNode menu = read(mvc.perform(get("/api/menu?restaurantId=" + restaurantId)));
        assertThat(menu.toString()).contains("Hakka Noodles");
        mvc.perform(delete("/api/merchant/categories/" + cat.get("id").asLong()).header("Authorization", bearer(merchant)))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/merchant/menu-items/" + item.get("id").asLong()).header("Authorization", bearer(merchant)))
                .andExpect(status().isNoContent());

        String code = "T" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        post("/api/merchant/coupons", merchant, Map.of("code", code, "discountType", "PERCENT", "discountValue", 150,
                "validFrom", LocalDate.now().toString(), "validTo", LocalDate.now().plusDays(5).toString()))
                .andExpect(status().isBadRequest());
        post("/api/merchant/coupons", merchant, Map.of("code", code, "discountType", "FLAT", "discountValue", 30,
                "validFrom", LocalDate.now().toString(), "validTo", LocalDate.now().plusDays(5).toString()))
                .andExpect(status().isCreated());
    }

    // ---------- helpers ----------

    private Map<String, Object> orderBody(String coupon, int points) {
        var m = new java.util.HashMap<String, Object>();
        m.put("restaurantId", restaurantId);
        m.put("items", List.of(Map.of("menuItemId", paneerTikkaId, "quantity", 2), Map.of("menuItemId", naanId, "quantity", 3)));
        m.put("couponCode", coupon);
        m.put("redeemPoints", points);
        m.put("orderType", "DINE_IN");
        m.put("tableNumber", "4");
        return m;
    }

    private int points() throws Exception {
        return read(mvc.perform(get("/api/users/me").header("Authorization", bearer(customer)))).get("loyaltyPoints").asInt();
    }

    private String login(String email) throws Exception {
        return read(post("/api/auth/signin", null, Map.of("login", email, "password", "Demo@123"))).get("token").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private ResultActions post(String url, String token, Object body) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", bearer(token));
        if (body != null) req.content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    private ResultActions put(String url, String token, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", bearer(token)).content(json.writeValueAsString(body)));
    }

    private ResultActions patch(String url, String token, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", bearer(token)).content(json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}

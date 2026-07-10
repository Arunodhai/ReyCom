package com.reydark.reycom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reydark.reycom.entity.User;
import com.reydark.reycom.enums.Role;
import com.reydark.reycom.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReyComBasicApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldRegisterAndLoginUser() throws Exception {
        String email = uniqueEmail("customer");

        // Step 1: Register a new customer through the public auth API.
        postJson("/api/auth/register", """
                {
                  "fullName": "Test Customer",
                  "email": "%s",
                  "password": "password123"
                }
                """.formatted(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Step 2: Login with the same credentials and read the JWT token.
        MvcResult loginResult = postJson("/api/auth/login", """
                {
                  "email": "%s",
                  "password": "password123"
                }
                """.formatted(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String token = extractStringFromJson(loginResult, "/data/token");
        assertThat(token).isNotBlank();

        // Step 3: Use the JWT token to call an authenticated endpoint.
        getJson("/api/users/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void shouldCompleteOrderPaymentFlow() throws Exception {
        String adminEmail = uniqueEmail("admin");
        String customerEmail = uniqueEmail("customer");

        // Step 1: Register an admin user, then promote that user directly in the test database.
        postJson("/api/auth/register", """
                {
                  "fullName": "Test Admin",
                  "email": "%s",
                  "password": "password123"
                }
                """.formatted(adminEmail))
                .andExpect(status().isCreated());

        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        // Step 2: Login as admin and keep the admin JWT for protected admin APIs.
        String adminToken = loginAndExtractToken(adminEmail);

        // Step 3: Create a category as admin.
        MvcResult categoryResult = postJson("/api/admin/categories", adminToken, """
                {
                  "name": "Test Electronics",
                  "description": "Products used by API integration tests"
                }
                """)
                .andExpect(status().isCreated())
                .andReturn();

        String categoryId = extractStringFromJson(categoryResult, "/data/id");

        // Step 4: Create a product as admin.
        MvcResult productResult = postJson("/api/admin/products", adminToken, """
                {
                  "name": "Test Keyboard",
                  "description": "Keyboard for integration test checkout",
                  "price": 1499.00,
                  "sku": "SKU-%s",
                  "imageUrl": null,
                  "categoryId": "%s"
                }
                """.formatted(UUID.randomUUID(), categoryId))
                .andExpect(status().isCreated())
                .andReturn();

        String productId = extractStringFromJson(productResult, "/data/id");

        // Step 5: Create inventory for the product so the order can pass stock checks.
        postJson("/api/admin/inventory", adminToken, """
                {
                  "productId": "%s",
                  "quantityAvailable": 10,
                  "reservedQuantity": 0,
                  "lowStockThreshold": 2
                }
                """.formatted(productId))
                .andExpect(status().isCreated());

        // Step 6: Register and login as a normal customer.
        postJson("/api/auth/register", """
                {
                  "fullName": "Test Customer",
                  "email": "%s",
                  "password": "password123"
                }
                """.formatted(customerEmail))
                .andExpect(status().isCreated());

        String customerToken = loginAndExtractToken(customerEmail);

        // Step 7: Add the product to the customer's cart.
        postJson("/api/cart/items", customerToken, """
                {
                  "productId": "%s",
                  "quantity": 2
                }
                """.formatted(productId))
                .andExpect(status().isCreated());

        // Step 8: Place an order from the current customer's cart.
        MvcResult orderResult = postJson("/api/orders", customerToken, "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"))
                .andReturn();

        String orderId = extractStringFromJson(orderResult, "/data/orderId");

        // Step 9: Initiate a simulated payment for that order.
        MvcResult paymentResult = postJson("/api/orders/" + orderId + "/payments", customerToken, """
                {
                  "paymentMethod": "UPI"
                }
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("INITIATED"))
                .andReturn();

        String paymentId = extractStringFromJson(paymentResult, "/data/paymentId");

        // Step 10: Simulate payment success.
        postJson("/api/payments/" + paymentId + "/success", customerToken, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // Step 11: Fetch customer orders and verify the order is now paid.
        getJson("/api/orders", customerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
    }

    private String loginAndExtractToken(String email) throws Exception {
        MvcResult loginResult = postJson("/api/auth/login", """
                {
                  "email": "%s",
                  "password": "password123"
                }
                """.formatted(email))
                .andExpect(status().isOk())
                .andReturn();

        return extractStringFromJson(loginResult, "/data/token");
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String url, String jsonBody) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String url, String token, String jsonBody) throws Exception {
        return mockMvc.perform(post(url)
                .header(HttpHeaders.AUTHORIZATION, authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }

    private org.springframework.test.web.servlet.ResultActions getJson(String url, String token) throws Exception {
        return mockMvc.perform(get(url)
                .header(HttpHeaders.AUTHORIZATION, authHeader(token))
                .accept(MediaType.APPLICATION_JSON));
    }

    private org.springframework.test.web.servlet.ResultActions putJson(String url, String token, String jsonBody) throws Exception {
        return mockMvc.perform(put(url)
                .header(HttpHeaders.AUTHORIZATION, authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }

    private String authHeader(String token) {
        return "Bearer " + token;
    }

    private String extractStringFromJson(MvcResult result, String jsonPointer) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseBody);
        return root.at(jsonPointer).asText();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.com";
    }
}

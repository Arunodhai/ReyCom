package com.reydark.reycom.service;

import com.reydark.reycom.dto.request.AddCartItemRequest;
import com.reydark.reycom.dto.request.UpdateCartItemRequest;
import com.reydark.reycom.dto.response.CartResponse;

import java.util.UUID;

public interface CartService {

    CartResponse getCurrentUserCart();

    CartResponse addItemToCart(AddCartItemRequest request);

    CartResponse updateCartItem(UUID itemId, UpdateCartItemRequest request);

    void removeCartItem(UUID itemId);

    void clearCart();
}

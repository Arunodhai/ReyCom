package com.reydark.reycom.service.impl;

import com.reydark.reycom.dto.request.AddCartItemRequest;
import com.reydark.reycom.dto.request.UpdateCartItemRequest;
import com.reydark.reycom.dto.response.CartResponse;
import com.reydark.reycom.entity.Cart;
import com.reydark.reycom.entity.CartItem;
import com.reydark.reycom.entity.Product;
import com.reydark.reycom.entity.User;
import com.reydark.reycom.exception.BadRequestException;
import com.reydark.reycom.exception.ResourceNotFoundException;
import com.reydark.reycom.exception.UnauthorizedException;
import com.reydark.reycom.mapper.CartMapper;
import com.reydark.reycom.repository.CartItemRepository;
import com.reydark.reycom.repository.CartRepository;
import com.reydark.reycom.repository.ProductRepository;
import com.reydark.reycom.repository.UserRepository;
import com.reydark.reycom.security.UserPrincipal;
import com.reydark.reycom.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CartResponse getCurrentUserCart() {
        Cart cart = getOrCreateCurrentUserCart();
        return CartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItemToCart(AddCartItemRequest request) {
        validateQuantity(request.quantity());

        Cart cart = getOrCreateCurrentUserCart();
        Product product = productRepository.findById(request.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .ifPresentOrElse(
                        item -> item.increaseQuantity(request.quantity()),
                        () -> addNewCartItem(cart, product, request.quantity())
                );

        return CartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(UUID itemId, UpdateCartItemRequest request) {
        validateQuantity(request.quantity());

        Cart cart = getOrCreateCurrentUserCart();
        CartItem item = findCurrentUserCartItem(cart, itemId);
        item.updateQuantity(request.quantity());

        return CartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public void removeCartItem(UUID itemId) {
        Cart cart = getOrCreateCurrentUserCart();
        CartItem item = findCurrentUserCartItem(cart, itemId);
        cart.removeItem(item);
    }

    @Override
    @Transactional
    public void clearCart() {
        Cart cart = getOrCreateCurrentUserCart();
        cart.getItems().clear();
    }

    private Cart getOrCreateCurrentUserCart() {
        User user = getCurrentUser();
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .user(user)
                        .build()));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication is required");
        }

        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private CartItem findCurrentUserCartItem(Cart cart, UUID itemId) {
        return cart.getItems()
                .stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
    }

    private void addNewCartItem(Cart cart, Product product, int quantity) {
        CartItem item = CartItem.builder()
                .product(product)
                .quantity(quantity)
                .unitPrice(product.getPrice())
                .build();
        item.recalculateLineTotal();
        cart.addItem(item);
        cartItemRepository.save(item);
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than 0");
        }
    }
}

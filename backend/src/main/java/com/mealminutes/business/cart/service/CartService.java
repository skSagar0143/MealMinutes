package com.mealminutes.business.cart.service;

import com.mealminutes.business.cart.api.AddCartItemRequest;
import com.mealminutes.business.cart.api.CartResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class CartService {
    private final JdbcTemplate jdbc;

    public CartService(@Qualifier("tenantBusinessJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc, "jdbc"); }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public CartResponse getActiveCart(String customerId, String branchId) {
        requireIdentity(customerId, branchId);
        List<String> carts = jdbc.query("""
                SELECT cart_id FROM customer_cart
                WHERE customer_user_id = ? AND branch_id = ? AND cart_status = 'ACTIVE'
                ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> rs.getString("cart_id"), customerId, branchId);
        return carts.isEmpty() ? emptyCart(branchId) : loadCart(carts.getFirst(), branchId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public CartResponse addItem(String customerId, AddCartItemRequest request) {
        Objects.requireNonNull(request, "request");
        requireIdentity(customerId, request.branchId());
        validateItemRequest(request);
        ensureActiveBranch(request.branchId());
        MenuPrice price = findAvailablePrice(request.itemId());
        String cartId = findOrCreateActiveCart(customerId, request.branchId());
        List<Integer> quantities = jdbc.query("""
                SELECT quantity FROM customer_cart_item WHERE cart_id = ? AND item_id = ? FOR UPDATE
                """, (rs, row) -> rs.getInt("quantity"), cartId, request.itemId());
        int newQuantity = request.quantity() + (quantities.isEmpty() ? 0 : quantities.getFirst());
        if (newQuantity > 99) throw new IllegalArgumentException("A menu item quantity cannot exceed 99");
        if (quantities.isEmpty()) {
            jdbc.update("""
                    INSERT INTO customer_cart_item
                    (cart_item_id, cart_id, item_id, quantity, unit_price_snapshot, item_notes)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), cartId, request.itemId(), newQuantity, price.price(), safeNotes(request.notes()));
        } else {
            jdbc.update("""
                    UPDATE customer_cart_item SET quantity = ?, unit_price_snapshot = ?, item_notes = ?
                    WHERE cart_id = ? AND item_id = ?
                    """, newQuantity, price.price(), safeNotes(request.notes()), cartId, request.itemId());
        }
        return loadCart(cartId, request.branchId());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public CartResponse updateQuantity(String customerId, String cartId, String itemId, int quantity) {
        requireIdentity(customerId, cartId);
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("itemId is required");
        if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("quantity must be between 1 and 99");
        String branchId = requireOwnedActiveCart(customerId, cartId);
        MenuPrice price = findAvailablePrice(itemId);
        int changed = jdbc.update("""
                UPDATE customer_cart_item SET quantity = ?, unit_price_snapshot = ?
                WHERE cart_id = ? AND item_id = ?
                """, quantity, price.price(), cartId, itemId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        return loadCart(cartId, branchId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public CartResponse removeItem(String customerId, String cartId, String itemId) {
        requireIdentity(customerId, cartId);
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("itemId is required");
        String branchId = requireOwnedActiveCart(customerId, cartId);
        int changed = jdbc.update("DELETE FROM customer_cart_item WHERE cart_id = ? AND item_id = ?", cartId, itemId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        return loadCart(cartId, branchId);
    }

    private String findOrCreateActiveCart(String customerId, String branchId) {
        List<String> carts = jdbc.query("""
                SELECT cart_id FROM customer_cart
                WHERE customer_user_id = ? AND branch_id = ? AND cart_status = 'ACTIVE'
                ORDER BY created_at DESC LIMIT 1 FOR UPDATE
                """, (rs, row) -> rs.getString("cart_id"), customerId, branchId);
        if (!carts.isEmpty()) return carts.getFirst();
        String cartId = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO customer_cart (cart_id, customer_user_id, branch_id, cart_status) VALUES (?, ?, ?, 'ACTIVE')",
                    cartId, customerId, branchId);
            return cartId;
        } catch (DuplicateKeyException concurrentCreate) {
            // V2's unique generated key permits one ACTIVE cart per customer/branch.
            // A competing request may have won the insert while this transaction waited.
            List<String> winner = jdbc.query("""
                    SELECT cart_id FROM customer_cart
                    WHERE customer_user_id = ? AND branch_id = ? AND cart_status = 'ACTIVE'
                    ORDER BY created_at DESC LIMIT 1 FOR UPDATE
                    """, (rs, row) -> rs.getString("cart_id"), customerId, branchId);
            if (winner.isEmpty()) throw concurrentCreate;
            return winner.getFirst();
        }
    }

    private String requireOwnedActiveCart(String customerId, String cartId) {
        List<String> branches = jdbc.query("""
                SELECT branch_id FROM customer_cart
                WHERE cart_id = ? AND customer_user_id = ? AND cart_status = 'ACTIVE' FOR UPDATE
                """, (rs, row) -> rs.getString("branch_id"), cartId, customerId);
        if (branches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active cart not found");
        return branches.getFirst();
    }

    private void ensureActiveBranch(String branchId) {
        List<String> branches = jdbc.query("SELECT branch_id FROM restaurant_branch WHERE branch_id = ? AND is_active = TRUE",
                (rs, row) -> rs.getString("branch_id"), branchId);
        if (branches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active branch not found");
    }

    private MenuPrice findAvailablePrice(String itemId) {
        List<MenuPrice> prices = jdbc.query("""
                SELECT item_id, price, currency FROM menu_item
                WHERE item_id = ? AND is_available = TRUE FOR UPDATE
                """, (rs, row) -> new MenuPrice(rs.getString("item_id"), rs.getBigDecimal("price"),
                rs.getString("currency")), itemId);
        if (prices.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Available menu item not found");
        return prices.getFirst();
    }

    private CartResponse loadCart(String cartId, String branchId) {
        List<CartResponse.CartItemResponse> items = jdbc.query("""
                SELECT ci.cart_item_id, ci.item_id, i.name, ci.quantity, ci.unit_price_snapshot,
                       i.currency, ci.item_notes
                FROM customer_cart_item ci
                INNER JOIN menu_item i ON i.item_id = ci.item_id
                WHERE ci.cart_id = ?
                ORDER BY ci.created_at ASC, ci.cart_item_id ASC
                """, (rs, row) -> {
            BigDecimal unit = rs.getBigDecimal("unit_price_snapshot");
            int quantity = rs.getInt("quantity");
            return new CartResponse.CartItemResponse(rs.getString("cart_item_id"), rs.getString("item_id"),
                    rs.getString("name"), quantity, unit, rs.getString("currency"),
                    unit.multiply(BigDecimal.valueOf(quantity)), rs.getString("item_notes"));
        }, cartId);
        BigDecimal subtotal = items.stream().map(CartResponse.CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cartId, branchId, "ACTIVE", items, subtotal);
    }

    private static CartResponse emptyCart(String branchId) {
        return new CartResponse(null, branchId, "ACTIVE", List.of(), BigDecimal.ZERO);
    }
    private static void requireIdentity(String customerId, String id) {
        if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("Authenticated customer is required");
        if (id == null || id.isBlank() || id.length() > 36) throw new IllegalArgumentException("A valid identifier is required");
    }
    private static void validateItemRequest(AddCartItemRequest request) {
        if (request.itemId() == null || request.itemId().isBlank() || request.itemId().length() > 36)
            throw new IllegalArgumentException("A valid itemId is required");
        if (request.quantity() < 1 || request.quantity() > 99)
            throw new IllegalArgumentException("quantity must be between 1 and 99");
        if (request.notes() != null && request.notes().length() > 500)
            throw new IllegalArgumentException("notes must be at most 500 characters");
    }
    private static String safeNotes(String notes) { return notes == null || notes.isBlank() ? null : notes.strip(); }
    private record MenuPrice(String itemId, BigDecimal price, String currency) { }
}

package com.mealminutes.business.cart.service;

import com.mealminutes.business.cart.api.AddCartItemRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked","rawtypes"})
class CartServiceTests {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CartService service=new CartService(jdbc);

    @Test void returnsEmptyCartWithoutCreatingOne(){
        when(jdbc.query(contains("FROM customer_cart"),any(RowMapper.class),eq("user-1"),eq("branch-1"))).thenReturn(List.of());
        var cart=service.getActiveCart("user-1","branch-1");
        assertThat(cart.cartId()).isNull();
        assertThat(cart.items()).isEmpty();
        assertThat(cart.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(jdbc,never()).update(contains("INSERT INTO customer_cart"),any(Object[].class));
    }

    @Test void rejectsInvalidIdentityAndQuantitiesBeforeDatabaseAccess(){
        assertThatThrownBy(()->service.getActiveCart(" ","branch-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.addItem("user-1",new AddCartItemRequest("branch-1","item-1",0,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.updateQuantity("user-1","cart-1","item-1",100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.removeItem("user-1","cart-1"," ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void addsAvailableItemAndComputesSubtotalFromPriceSnapshot(){
        when(jdbc.query(contains("FROM restaurant_branch"),any(RowMapper.class),eq("branch-1"))).thenReturn(List.of("branch-1"));
        when(jdbc.query(contains("FROM menu_item"),any(RowMapper.class),eq("item-1")))
                .thenReturn(List.of(new CartService.MenuPrice("item-1",new BigDecimal("12.50"),"INR")));
        when(jdbc.query(contains("FROM customer_cart"),any(RowMapper.class),eq("user-1"),eq("branch-1"))).thenReturn(List.of("cart-1"));
        when(jdbc.query(contains("SELECT quantity FROM customer_cart_item"),any(RowMapper.class),eq("cart-1"),eq("item-1"))).thenReturn(List.of(1));
        when(jdbc.query(contains("FROM customer_cart_item ci"),any(RowMapper.class),eq("cart-1")))
                .thenReturn(List.of(new com.mealminutes.business.cart.api.CartResponse.CartItemResponse(
                        "ci-1","item-1","Tea",2,new BigDecimal("12.50"),"INR",new BigDecimal("25.00"),null)));
        var result=service.addItem("user-1",new AddCartItemRequest("branch-1","item-1",1,null));
        assertThat(result.items()).hasSize(1);
        assertThat(result.subtotal()).isEqualByComparingTo("25.00");
        verify(jdbc).update(contains("UPDATE customer_cart_item"),eq(2),eq(new BigDecimal("12.50")),isNull(),eq("cart-1"),eq("item-1"));
    }

    @Test void createsCartAndAddsFirstItemWhenNoActiveCartExists(){
        when(jdbc.query(contains("FROM restaurant_branch"),any(RowMapper.class),eq("branch-1"))).thenReturn(List.of("branch-1"));
        when(jdbc.query(contains("FROM menu_item"),any(RowMapper.class),eq("item-1")))
                .thenReturn(List.of(new CartService.MenuPrice("item-1",new BigDecimal("5.00"),"INR")));
        when(jdbc.query(contains("FROM customer_cart"),any(RowMapper.class),eq("user-1"),eq("branch-1"))).thenReturn(List.of());
        when(jdbc.query(contains("SELECT quantity FROM customer_cart_item"),any(RowMapper.class),anyString(),eq("item-1"))).thenReturn(List.of());
        when(jdbc.query(contains("FROM customer_cart_item ci"),any(RowMapper.class),anyString())).thenReturn(List.of());
        var result=service.addItem("user-1",new AddCartItemRequest("branch-1","item-1",1,"  less spicy  "));
        assertThat(result.items()).isEmpty();
        verify(jdbc).update(contains("INSERT INTO customer_cart "),anyString(),eq("user-1"),eq("branch-1"));
        verify(jdbc).update(contains("INSERT INTO customer_cart_item"),anyString(),anyString(),eq("item-1"),eq(1),eq(new BigDecimal("5.00")),eq("less spicy"));
    }

    @Test void updatesQuantityAndRemovesItemsFromOwnedCart(){
        when(jdbc.query(contains("SELECT branch_id FROM customer_cart"),any(RowMapper.class),eq("user-1"),eq("cart-1"))).thenReturn(List.of("branch-1"));
        when(jdbc.query(contains("FROM menu_item"),any(RowMapper.class),eq("item-1")))
                .thenReturn(List.of(new CartService.MenuPrice("item-1",new BigDecimal("7.00"),"INR")));
        when(jdbc.update(contains("UPDATE customer_cart_item"),eq(3),eq(new BigDecimal("7.00")),eq("cart-1"),eq("item-1"))).thenReturn(1);
        when(jdbc.query(contains("FROM customer_cart_item ci"),any(RowMapper.class),eq("cart-1"))).thenReturn(List.of());
        assertThat(service.updateQuantity("user-1","cart-1","item-1",3).items()).isEmpty();
        when(jdbc.update(contains("DELETE FROM customer_cart_item"),eq("cart-1"),eq("item-1"))).thenReturn(1);
        assertThat(service.removeItem("user-1","cart-1","item-1").subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test void rejectsMissingBranchOrUnavailableItem(){
        when(jdbc.query(contains("FROM restaurant_branch"),any(RowMapper.class),eq("missing"))).thenReturn(List.of());
        assertThatThrownBy(()->service.addItem("user-1",new AddCartItemRequest("missing","item-1",1,null)))
                .isInstanceOf(ResponseStatusException.class);
        when(jdbc.query(contains("FROM restaurant_branch"),any(RowMapper.class),eq("branch-1"))).thenReturn(List.of("branch-1"));
        when(jdbc.query(contains("FROM menu_item"),any(RowMapper.class),eq("missing-item"))).thenReturn(List.of());
        assertThatThrownBy(()->service.addItem("user-1",new AddCartItemRequest("branch-1","missing-item",1,null)))
                .isInstanceOf(ResponseStatusException.class);
    }
}

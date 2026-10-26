package com.mealminutes.business.menu.api;

import com.mealminutes.business.menu.service.TenantMenuQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StorefrontMenuControllerTests {
    private final TenantMenuQueryService service=mock(TenantMenuQueryService.class);
    private final StorefrontMenuController controller=new StorefrontMenuController(service);

    @Test void returnsRestaurantCategoriesAndFilteredItems() {
        var restaurant=new RestaurantProfileResponse("r1","Local Kitchen",null,null,null,null,"INR","Asia/Kolkata");
        var category=new MenuCategoryResponse("c1","Mains","mains",null,1);
        var item=new MenuItemResponse("i1","c1","Tea","tea",null,new BigDecimal("10.00"),"INR",null,true,false,null,true,1);
        when(service.getRestaurantProfile()).thenReturn(Optional.of(restaurant));
        when(service.listCategories()).thenReturn(List.of(category));
        when(service.listItems("mains","tea",1,25)).thenReturn(List.of(item));
        assertThat(controller.restaurant()).isEqualTo(restaurant);
        assertThat(controller.categories()).containsExactly(category);
        assertThat(controller.items("mains","tea",1,25)).containsExactly(item);
        verify(service).listItems("mains","tea",1,25);
    }

    @Test void returnsNotFoundWhenTenantHasNoActiveRestaurantProfile() {
        when(service.getRestaurantProfile()).thenReturn(Optional.empty());
        assertThatThrownBy(controller::restaurant).isInstanceOf(ResponseStatusException.class);
    }
}
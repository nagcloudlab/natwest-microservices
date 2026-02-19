package com.ftgo.restaurant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @GetMapping
    public List<Restaurant> getAllRestaurants() {
        return restaurantService.getAllRestaurants();
    }

    @GetMapping("/{id}")
    public Restaurant getRestaurant(@PathVariable Long id) {
        return restaurantService.getRestaurant(id);
    }

    @PostMapping
    public Restaurant createRestaurant(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String address = body.get("address");
        String phone = body.get("phone");
        return restaurantService.createRestaurant(name, address, phone);
    }

    @GetMapping("/{id}/menu")
    public List<MenuItem> getMenu(@PathVariable Long id) {
        Restaurant restaurant = restaurantService.getRestaurant(id);
        return restaurant.getMenuItems();
    }

    /**
     * New endpoint: fetch menu items by a list of IDs.
     * Used by the monolith's OrderService when building an order.
     * Example: GET /api/restaurants/menu-items?ids=1,2,3
     */
    @GetMapping("/menu-items")
    public List<MenuItem> getMenuItemsByIds(@RequestParam List<Long> ids) {
        return restaurantService.getMenuItemsByIds(ids);
    }
}

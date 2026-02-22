package com.ftgo.web;

import com.ftgo.order.CreateOrderRequest;
import com.ftgo.order.OrderItemRequest;
import com.ftgo.order.OrderResponse;
import com.ftgo.order.OrderService;
import com.ftgo.restaurant.MenuItem;
import com.ftgo.restaurant.Restaurant;
import com.ftgo.restaurant.RestaurantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/consumer")
public class ConsumerWebController {

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private OrderService orderService;

    @GetMapping("/restaurants")
    public String browseRestaurants(Model model) {
        model.addAttribute("restaurants", restaurantService.getAllRestaurants());
        return "consumer/restaurants";
    }

    @GetMapping("/restaurants/{id}/menu")
    public String viewMenu(@PathVariable Long id, Model model) {
        Restaurant restaurant = restaurantService.getRestaurant(id);
        model.addAttribute("restaurant", restaurant);
        return "consumer/menu";
    }

    @PostMapping("/orders")
    public String placeOrder(
            @RequestParam Long restaurantId,
            @RequestParam String consumerName,
            @RequestParam String consumerContact,
            @RequestParam String deliveryAddress,
            @RequestParam String paymentMethod,
            @RequestParam List<Long> menuItemIds,
            @RequestParam List<Integer> quantities,
            RedirectAttributes redirectAttributes) {

        try {
            List<OrderItemRequest> items = new ArrayList<>();
            for (int i = 0; i < menuItemIds.size(); i++) {
                int qty = quantities.get(i);
                if (qty > 0) {
                    items.add(new OrderItemRequest(menuItemIds.get(i), qty));
                }
            }

            if (items.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select at least one item.");
                return "redirect:/consumer/restaurants/" + restaurantId + "/menu";
            }

            CreateOrderRequest request = new CreateOrderRequest(
                    1L, // default seed consumer
                    consumerName,
                    consumerContact,
                    restaurantId,
                    deliveryAddress,
                    paymentMethod,
                    items
            );

            OrderResponse order = orderService.createOrder(request);
            return "redirect:/consumer/orders/" + order.id() + "/confirmation";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/consumer/restaurants/" + restaurantId + "/menu";
        }
    }

    @GetMapping("/orders/{id}/confirmation")
    public String orderConfirmation(@PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.getOrder(id));
        return "consumer/order-confirmation";
    }

    @GetMapping("/orders")
    public String myOrders(@RequestParam(required = false) Long consumerId, Model model) {
        if (consumerId != null) {
            model.addAttribute("orders", orderService.getOrdersByConsumerId(consumerId));
            model.addAttribute("consumerId", consumerId);
        }
        return "consumer/my-orders";
    }
}

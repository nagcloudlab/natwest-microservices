package com.ftgo.web;

import com.ftgo.kitchen.KitchenService;
import com.ftgo.restaurant.RestaurantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/restaurant")
public class RestaurantWebController {

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private KitchenService kitchenService;

    @GetMapping
    public String restaurantDashboard(Model model) {
        model.addAttribute("restaurants", restaurantService.getAllRestaurants());
        return "restaurant/dashboard";
    }

    @GetMapping("/{id}/tickets")
    public String restaurantTickets(@PathVariable Long id, Model model) {
        model.addAttribute("restaurant", restaurantService.getRestaurant(id));
        model.addAttribute("tickets", kitchenService.getTicketsByRestaurantId(id));
        return "restaurant/tickets";
    }

    @PostMapping("/tickets/{ticketId}/accept")
    public String acceptTicket(
            @PathVariable Long ticketId,
            @RequestParam Long restaurantId,
            RedirectAttributes redirectAttributes) {
        try {
            kitchenService.acceptTicket(ticketId);
            redirectAttributes.addFlashAttribute("success", "Ticket #" + ticketId + " accepted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/restaurant/" + restaurantId + "/tickets";
    }

    @PostMapping("/tickets/{ticketId}/preparing")
    public String startPreparing(
            @PathVariable Long ticketId,
            @RequestParam Long restaurantId,
            RedirectAttributes redirectAttributes) {
        try {
            kitchenService.startPreparation(ticketId);
            redirectAttributes.addFlashAttribute("success", "Ticket #" + ticketId + " is now being prepared.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/restaurant/" + restaurantId + "/tickets";
    }

    @PostMapping("/tickets/{ticketId}/ready")
    public String markReady(
            @PathVariable Long ticketId,
            @RequestParam Long restaurantId,
            RedirectAttributes redirectAttributes) {
        try {
            kitchenService.markReady(ticketId);
            redirectAttributes.addFlashAttribute("success", "Ticket #" + ticketId + " is ready for pickup!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/restaurant/" + restaurantId + "/tickets";
    }
}

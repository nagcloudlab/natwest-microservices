package com.example.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.dto.TransferRequest;
import com.example.exception.BalanceException;
import com.example.service.TransferService;

@Controller
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping("/transfer")
    public String showTransferForm(@RequestParam(value = "success", required = false) String success, Model model) {
        if (success != null) {
            model.addAttribute("message", "Transfer successful!");
        }

        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        String username = authentication.getName();
        model.addAttribute("username", username);
        authentication.getAuthorities().forEach(auth -> model.addAttribute("role", auth.getAuthority()));

        return "transfer-form";
    }

    @PostMapping("/transfer")
    public String handleTransfer(@ModelAttribute TransferRequest transferRequest) {
        transferService.transfer(transferRequest.getFromAccountId(), transferRequest.getToAccountId(),
                transferRequest.getAmount());
        return "redirect:/transfer?success";
    }

    @ExceptionHandler(BalanceException.class)
    public String handleBalanceException(BalanceException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "transfer-form";
    }

}

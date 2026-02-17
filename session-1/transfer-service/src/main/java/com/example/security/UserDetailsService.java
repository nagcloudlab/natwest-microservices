package com.example.security;

import org.springframework.stereotype.Service;

@Service
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {
    @Override
    public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String username)
            throws org.springframework.security.core.userdetails.UsernameNotFoundException {
        // For simplicity, we are hardcoding a user. In a real application, you would
        // fetch this from a database.
        if ("nag".equals(username)) {
            return org.springframework.security.core.userdetails.User.withUsername("nag")
                    .password("{noop}password") // {noop} indicates that the password is not encoded
                    .roles("USER")
                    .build();
        } else {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found");
        }
    }

}

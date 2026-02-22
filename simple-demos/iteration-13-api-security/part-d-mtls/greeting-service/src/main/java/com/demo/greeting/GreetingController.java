package com.demo.greeting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class GreetingController {

    @GetMapping("/greeting")
    public Map<String, String> greeting(HttpServletRequest request) throws UnknownHostException {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Hello from Greeting Service (mTLS)!");

        X509Certificate[] certs = (X509Certificate[]) request.getAttribute(
                "jakarta.servlet.request.X509Certificate");
        if (certs != null && certs.length > 0) {
            response.put("clientCert", certs[0].getSubjectX500Principal().getName());
        }

        response.put("host", InetAddress.getLocalHost().getHostName());
        return response;
    }
}

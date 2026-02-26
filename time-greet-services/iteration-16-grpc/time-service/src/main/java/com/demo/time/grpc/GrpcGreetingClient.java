package com.demo.time.grpc;

import com.demo.greeting.grpc.proto.GreetingProto;
import com.demo.greeting.grpc.proto.GreetingServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GrpcGreetingClient {

    @GrpcClient("greeting-service")
    private GreetingServiceGrpc.GreetingServiceBlockingStub greetingStub;

    public Map<String, String> getGreeting() {
        GreetingProto.GreetingResponse response = greetingStub.getGreeting(
                GreetingProto.GreetingRequest.newBuilder().build()
        );

        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", response.getMessage());
        result.put("host", response.getHost());
        return result;
    }
}

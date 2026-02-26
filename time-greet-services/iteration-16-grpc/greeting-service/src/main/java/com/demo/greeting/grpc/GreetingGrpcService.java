package com.demo.greeting.grpc;

import com.demo.greeting.grpc.proto.GreetingProto;
import com.demo.greeting.grpc.proto.GreetingServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.net.InetAddress;
import java.net.UnknownHostException;

@GrpcService
public class GreetingGrpcService extends GreetingServiceGrpc.GreetingServiceImplBase {

    @Override
    public void getGreeting(GreetingProto.GreetingRequest request,
                            StreamObserver<GreetingProto.GreetingResponse> responseObserver) {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();

            GreetingProto.GreetingResponse response = GreetingProto.GreetingResponse.newBuilder()
                    .setMessage("Hello from Greeting Service!")
                    .setHost(hostname)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UnknownHostException e) {
            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Failed to get hostname")
                            .asRuntimeException()
            );
        }
    }
}

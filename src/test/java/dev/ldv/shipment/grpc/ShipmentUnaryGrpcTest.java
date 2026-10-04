package dev.ldv.shipment.grpc;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ShipmentUnaryGrpcTest {

    @GrpcClient("shipmentService")
    private ShipmentService client;

    @Test
    void createShipmentReturnsCreatedShipment() {
        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber("tracking-number-1")
                .setOrigin("origin-1")
                .setDestination("destination-1")
                .build();

        Shipment shipment = client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        assertNotNull(shipment.getId());
        assertFalse(shipment.getId().isBlank());
        assertEquals(createShipmentRequest.getTrackingNumber(), shipment.getTrackingNumber());
        assertEquals(createShipmentRequest.getOrigin(), shipment.getOrigin());
        assertEquals(createShipmentRequest.getDestination(), shipment.getDestination());
        assertEquals(ShipmentStatus.CREATED, shipment.getStatus());
    }

    @Test
    void getShipmentReturnsPreviouslyCreatedShipment() {
        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber("tracking-number-2")
                .setOrigin("origin-2")
                .setDestination("destination-2")
                .build();

        Shipment createShipmentResponse = client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        GetShipmentRequest getShipmentRequest = GetShipmentRequest.newBuilder()
                .setShipmentId(createShipmentResponse.getId())
                .build();

        Shipment getShipmentResponse = client.getShipment(getShipmentRequest).await().atMost(Duration.ofSeconds(5));

        assertEquals(createShipmentResponse, getShipmentResponse);
    }
}

package dev.ldv.shipment.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ShipmentValidationGrpcTest {

    private static final String BLANK_STRING = "";

    @GrpcClient("shipmentService")
    private ShipmentService client;

    @Test
    void blankTrackingNumberReturnsInvalidArgument() {
        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber(BLANK_STRING)
                .setOrigin("origin")
                .setDestination("destination")
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5))
        );

        assertEquals(Status.INVALID_ARGUMENT.getCode(), exception.getStatus().getCode());
    }

    @Test
    void duplicateTrackingNumberReturnsAlreadyExists() {
        String trackingNumber = UUID.randomUUID().toString();

        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber(trackingNumber)
                .setOrigin("origin")
                .setDestination("destination")
                .build();

        client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5))
        );

        assertEquals(Status.ALREADY_EXISTS.getCode(), exception.getStatus().getCode());
    }

    @Test
    void blankShipmentIdReturnsInvalidArgument() {
        String trackingNumber = UUID.randomUUID().toString();

        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber(trackingNumber)
                .setOrigin("origin")
                .setDestination("destination")
                .build();

        client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        Instant instant = Instant.parse("2026-08-23T10:00:00Z");
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();

        Checkpoint checkpoint = Checkpoint.newBuilder()
                .setShipmentId(BLANK_STRING)
                .setStatus(ShipmentStatus.IN_TRANSIT)
                .setLocation("location")
                .setOccurredAt(timestamp)
                .build();

        AddCheckpointRequest addCheckpointRequest = AddCheckpointRequest.newBuilder()
                .setCheckpoint(checkpoint)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.addCheckpoint(addCheckpointRequest).await().atMost(Duration.ofSeconds(5))
        );

        assertEquals(Status.INVALID_ARGUMENT.getCode(), exception.getStatus().getCode());
    }

    @Test
    void unknownShipmentReturnsNotFound() {
        GetShipmentRequest getShipmentRequest = GetShipmentRequest.newBuilder()
                .setShipmentId(UUID.randomUUID().toString())
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.getShipment(getShipmentRequest).await().atMost(Duration.ofSeconds(5))
        );

        assertEquals(Status.NOT_FOUND.getCode(), exception.getStatus().getCode());
    }

    @Test
    void successfulCheckpoint() {
        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber(UUID.randomUUID().toString())
                .setOrigin("origin")
                .setDestination("destination")
                .build();

        Shipment shipment = client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        Instant instant = Instant.parse("2026-10-08T10:00:00Z");
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
        Checkpoint checkpoint = Checkpoint.newBuilder()
                .setShipmentId(shipment.getId())
                .setStatus(ShipmentStatus.IN_TRANSIT)
                .setLocation("location")
                .setOccurredAt(timestamp)
                .build();
        AddCheckpointRequest addCheckpointRequest = AddCheckpointRequest.newBuilder()
                .setCheckpoint(checkpoint)
                .build();

        ShipmentEvent shipmentEvent = client.addCheckpoint(addCheckpointRequest).await().atMost(Duration.ofSeconds(5));
        assertEquals(checkpoint, shipmentEvent.getCheckpoint());
        assertEquals(shipment.getId(), shipmentEvent.getShipment().getId());
        assertEquals(shipment.getTrackingNumber(), shipmentEvent.getShipment().getTrackingNumber());
        assertEquals(ShipmentStatus.IN_TRANSIT, shipmentEvent.getShipment().getStatus());
        assertEquals(shipment.getOrigin(), shipmentEvent.getShipment().getOrigin());
        assertEquals(shipment.getDestination(), shipmentEvent.getShipment().getDestination());
        assertEquals(2, shipmentEvent.getShipment().getCheckpointsCount());
        assertIterableEquals(List.of(ShipmentStatus.CREATED, ShipmentStatus.IN_TRANSIT),
                shipmentEvent.getShipment().getCheckpointsList().stream()
                        .map(Checkpoint::getStatus)
                        .toList());
    }

    @Test
    void forbiddenTransitionReturnsFailedPrecondition() {
        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber(UUID.randomUUID().toString())
                .setOrigin("origin")
                .setDestination("destination")
                .build();

        Shipment shipment = client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        Instant instant = Instant.parse("2026-10-08T10:00:00Z");
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
        Checkpoint checkpoint = Checkpoint.newBuilder()
                .setShipmentId(shipment.getId())
                .setStatus(ShipmentStatus.DELIVERED)
                .setLocation("location")
                .setOccurredAt(timestamp)
                .build();
        AddCheckpointRequest addCheckpointRequest = AddCheckpointRequest.newBuilder()
                .setCheckpoint(checkpoint)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.addCheckpoint(addCheckpointRequest).await().atMost(Duration.ofSeconds(5))
        );

        assertEquals(Status.FAILED_PRECONDITION.getCode(), exception.getStatus().getCode());
    }

    @Test
    void absentCheckpointReturnsInvalidArgument() {
        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber(UUID.randomUUID().toString())
                .setOrigin("origin")
                .setDestination("destination")
                .build();

        client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        AddCheckpointRequest addCheckpointRequest = AddCheckpointRequest.newBuilder()
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.addCheckpoint(addCheckpointRequest).await().atMost(Duration.ofSeconds(5))
        );

        assertEquals(Status.INVALID_ARGUMENT.getCode(), exception.getStatus().getCode());
    }

    @Test
    void invalidCheckpointStatusReturnsInvalidArgument() {
        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber(UUID.randomUUID().toString())
                .setOrigin("origin")
                .setDestination("destination")
                .build();

        Shipment shipment = client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        Instant instant = Instant.parse("2026-10-08T10:00:00Z");
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
        Checkpoint checkpoint = Checkpoint.newBuilder()
                .setShipmentId(shipment.getId())
                .setStatus(ShipmentStatus.SHIPMENT_STATUS_UNSPECIFIED)
                .setLocation("location")
                .setOccurredAt(timestamp)
                .build();
        AddCheckpointRequest addCheckpointRequest = AddCheckpointRequest.newBuilder()
                .setCheckpoint(checkpoint)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.addCheckpoint(addCheckpointRequest).await().atMost(Duration.ofSeconds(5))
        );

        assertEquals(Status.INVALID_ARGUMENT.getCode(), exception.getStatus().getCode());
    }

    @Test
    void unknownShipmentInCheckpointReturnsNotFound() {
        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber(UUID.randomUUID().toString())
                .setOrigin("origin")
                .setDestination("destination")
                .build();

        client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        Instant instant = Instant.parse("2026-10-08T10:00:00Z");
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
        Checkpoint checkpoint = Checkpoint.newBuilder()
                .setShipmentId(UUID.randomUUID().toString())
                .setStatus(ShipmentStatus.IN_TRANSIT)
                .setLocation("location")
                .setOccurredAt(timestamp)
                .build();
        AddCheckpointRequest addCheckpointRequest = AddCheckpointRequest.newBuilder()
                .setCheckpoint(checkpoint)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.addCheckpoint(addCheckpointRequest).await().atMost(Duration.ofSeconds(5))
        );

        assertEquals(Status.NOT_FOUND.getCode(), exception.getStatus().getCode());
    }

    @Test
    void terminalShipmentStatusRejectsTransitionAndReturnsFailedPrecondition() {
        CreateShipmentRequest createShipmentRequest = CreateShipmentRequest.newBuilder()
                .setTrackingNumber(UUID.randomUUID().toString())
                .setOrigin("origin")
                .setDestination("destination")
                .build();

        Shipment shipment = client.createShipment(createShipmentRequest).await().atMost(Duration.ofSeconds(5));

        Instant instant = Instant.parse("2026-10-08T10:00:00Z");
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
        Checkpoint checkpoint = Checkpoint.newBuilder()
                .setShipmentId(shipment.getId())
                .setStatus(ShipmentStatus.CANCELLED)
                .setLocation("location")
                .setOccurredAt(timestamp)
                .build();
        AddCheckpointRequest addCheckpointRequest = AddCheckpointRequest.newBuilder()
                .setCheckpoint(checkpoint)
                .build();

        client.addCheckpoint(addCheckpointRequest).await().atMost(Duration.ofSeconds(5));

        Checkpoint rejectedCheckpoint = Checkpoint.newBuilder()
                .setShipmentId(shipment.getId())
                .setStatus(ShipmentStatus.DELIVERED)
                .setLocation("location")
                .setOccurredAt(timestamp)
                .build();
        AddCheckpointRequest rejectedCheckpointRequest = AddCheckpointRequest.newBuilder()
                .setCheckpoint(rejectedCheckpoint)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.addCheckpoint(rejectedCheckpointRequest).await().atMost(Duration.ofSeconds(5))
        );

        assertEquals(Status.FAILED_PRECONDITION.getCode(), exception.getStatus().getCode());
    }
}

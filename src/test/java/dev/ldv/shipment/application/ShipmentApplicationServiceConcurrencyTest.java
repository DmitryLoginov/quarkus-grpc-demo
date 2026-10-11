package dev.ldv.shipment.application;

import dev.ldv.shipment.domain.*;
import dev.ldv.shipment.repository.InMemoryShipmentRepository;
import dev.ldv.shipment.repository.ShipmentRepository;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ShipmentApplicationServiceConcurrencyTest {

    @Test
    void concurrentTransition() {
        ShipmentApplicationService applicationService = getShipmentApplicationService();

        Shipment shipment = applicationService.create(UUID.randomUUID().toString(), "origin", "destination");

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        int awaitTerminationSeconds = 1;

        try {
            List<CompletableFuture<ShipmentUpdate>> futureList = new ArrayList<>();
            int allOfTimeoutSeconds = 5;

            for (int i = 0; i < 2; i++) {
                futureList.add(CompletableFuture.supplyAsync(
                        () -> applicationService.addCheckpoint(shipment.id(),
                                ShipmentStatus.IN_TRANSIT,
                                "location",
                                Instant.parse("2026-10-08T10:00:00Z")), executorService)
                );
            }

            CompletableFuture<Void> allOf = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]));
            assertThrows(ExecutionException.class, () -> allOf.get(allOfTimeoutSeconds, TimeUnit.SECONDS));

            var map = futureList.stream()
                    .collect(Collectors.partitioningBy(CompletableFuture::isCompletedExceptionally));

            assertEquals(1, map.get(true).size());
            assertEquals(1, map.get(false).size());

            var exceptionalFuture = map.get(true).getFirst();

            try {
                exceptionalFuture.get();
            } catch (ExecutionException executionException) {
                assertInstanceOf(ShipmentException.class, executionException.getCause());
                ShipmentException shipmentException = (ShipmentException) executionException.getCause();
                assertEquals(ShipmentException.Category.INVALID_STATUS_TRANSITION, shipmentException.getCategory());
                assertTrue(shipmentException.getMessage().contains("Invalid shipment status transition"));
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                fail("interrupted");
            }

            var successfulFuture = map.get(false).getFirst();

            try {
                ShipmentUpdate shipmentUpdate = successfulFuture.get();
                assertEquals(ShipmentStatus.IN_TRANSIT, shipmentUpdate.checkpoint().status());
            } catch (ExecutionException e) {
                fail("failed: " + e.getCause().getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted");
            }

            Shipment updatedShipment = applicationService.getById(shipment.id());

            assertEquals(2, updatedShipment.checkpoints().size());
            assertIterableEquals(List.of(ShipmentStatus.CREATED, ShipmentStatus.IN_TRANSIT),
                    updatedShipment.checkpoints().stream()
                            .map(Checkpoint::status)
                            .toList());
        } finally {
            executorService.shutdown();

            try {
                if (!executorService.awaitTermination(awaitTerminationSeconds, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(awaitTerminationSeconds, TimeUnit.SECONDS)) {
                        fail("Executor did not terminate");
                    }
                }
            } catch (InterruptedException ignored) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static @NonNull ShipmentApplicationService getShipmentApplicationService() {
        InMemoryShipmentRepository repository = new InMemoryShipmentRepository();
        CyclicBarrier barrier = new CyclicBarrier(2);
        int awaitBarrierSeconds = 5;

        ShipmentRepository proxyRepository = new ShipmentRepository() {
            @Override
            public Shipment save(Shipment shipment) {
                return repository.save(shipment);
            }

            @Override
            public Optional<Shipment> findById(String id) {
                return repository.findById(id);
            }

            @Override
            public Shipment saveIfTrackingNumberNotExists(Shipment shipment) {
                return repository.saveIfTrackingNumberNotExists(shipment);
            }

            @Override
            public Shipment update(String id, Function<Shipment, Shipment> updateFunction) {
                try {
                    barrier.await(awaitBarrierSeconds, TimeUnit.SECONDS);
                    return repository.update(id, updateFunction);
                } catch (BrokenBarrierException brokenBarrierException) {
                    throw new RuntimeException("barrier is broken", brokenBarrierException);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("interrupted", interruptedException);
                } catch (TimeoutException timeoutException) {
                    throw new RuntimeException("timeout", timeoutException);
                }
            }
        };

        return new ShipmentApplicationService(proxyRepository, Clock.systemUTC());
    }
}

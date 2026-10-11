package dev.ldv.shipment.application;

import dev.ldv.shipment.domain.ShipmentException;
import dev.ldv.shipment.domain.ShipmentStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static dev.ldv.shipment.domain.ShipmentStatusTransitionValidator.validateTransition;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ShipmentTransitionsTest {

    @ParameterizedTest
    @EnumSource(ShipmentStatus.class)
    void shouldRejectStatusRepeat(ShipmentStatus shipmentStatus) {
        assertThrows(ShipmentException.class, () -> validateTransition(shipmentStatus, shipmentStatus));
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"DELIVERED"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectTransitionForDeliveredShipment(ShipmentStatus shipmentStatus) {
        assertThrows(ShipmentException.class, () -> validateTransition(ShipmentStatus.DELIVERED, shipmentStatus));
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectTransitionForCancelledShipment(ShipmentStatus shipmentStatus) {
        assertThrows(ShipmentException.class, () -> validateTransition(ShipmentStatus.CANCELLED, shipmentStatus));
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"IN_TRANSIT", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectInvalidTransitionForCreatedShipment(ShipmentStatus shipmentStatus) {
        assertThrows(ShipmentException.class, () -> validateTransition(ShipmentStatus.CREATED, shipmentStatus));
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"IN_TRANSIT", "CANCELLED"}, mode = EnumSource.Mode.INCLUDE)
    void shouldAcceptValidTransitionForCreatedShipment(ShipmentStatus shipmentStatus) {
        assertDoesNotThrow(() -> validateTransition(ShipmentStatus.CREATED, shipmentStatus));
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"OUT_FOR_DELIVERY", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectInvalidTransitionForShipmentInTransit(ShipmentStatus shipmentStatus) {
        assertThrows(ShipmentException.class, () -> validateTransition(ShipmentStatus.IN_TRANSIT, shipmentStatus));
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"OUT_FOR_DELIVERY", "CANCELLED"}, mode = EnumSource.Mode.INCLUDE)
    void shouldAcceptValidTransitionForShipmentInTransit(ShipmentStatus shipmentStatus) {
        assertDoesNotThrow(() -> validateTransition(ShipmentStatus.IN_TRANSIT, shipmentStatus));
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"DELIVERED", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectInvalidTransitionForShipmentOutForDelivery(ShipmentStatus shipmentStatus) {
        assertThrows(ShipmentException.class, () -> validateTransition(ShipmentStatus.OUT_FOR_DELIVERY, shipmentStatus));
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"DELIVERED", "CANCELLED"}, mode = EnumSource.Mode.INCLUDE)
    void shouldAcceptValidTransitionForShipmentOutForDelivery(ShipmentStatus shipmentStatus) {
        assertDoesNotThrow(() -> validateTransition(ShipmentStatus.OUT_FOR_DELIVERY, shipmentStatus));
    }
}

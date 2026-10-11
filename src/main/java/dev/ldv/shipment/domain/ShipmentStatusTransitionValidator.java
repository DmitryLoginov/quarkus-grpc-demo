package dev.ldv.shipment.domain;

import io.quarkus.logging.Log;

public final class ShipmentStatusTransitionValidator {

    private ShipmentStatusTransitionValidator() {
    }

    public static void validateTransition(ShipmentStatus oldStatus, ShipmentStatus newStatus) {
        Log.debugf("Old status: %s", oldStatus);
        Log.debugf("New status: %s", newStatus);

        if (oldStatus == newStatus) {
            throw new ShipmentException("Invalid shipment status transition: shipment is already in requested status",
                    ShipmentException.Category.INVALID_STATUS_TRANSITION);
        }

        if (oldStatus == ShipmentStatus.DELIVERED || oldStatus == ShipmentStatus.CANCELLED) {
            throw new ShipmentException("Invalid shipment status transition: current shipment status is terminal and can't be changed",
                    ShipmentException.Category.INVALID_STATUS_TRANSITION);
        }

        switch (oldStatus) {
            case CREATED: {
                if (!(newStatus == ShipmentStatus.IN_TRANSIT || newStatus == ShipmentStatus.CANCELLED)) {
                    throw new ShipmentException("Invalid shipment status transition",
                            ShipmentException.Category.INVALID_STATUS_TRANSITION);
                }
                break;
            }
            case IN_TRANSIT: {
                if (!(newStatus == ShipmentStatus.OUT_FOR_DELIVERY || newStatus == ShipmentStatus.CANCELLED)) {
                    throw new ShipmentException("Invalid shipment status transition",
                            ShipmentException.Category.INVALID_STATUS_TRANSITION);
                }
                break;
            }
            case OUT_FOR_DELIVERY: {
                if (!(newStatus == ShipmentStatus.DELIVERED || newStatus == ShipmentStatus.CANCELLED)) {
                    throw new ShipmentException("Invalid shipment status transition",
                            ShipmentException.Category.INVALID_STATUS_TRANSITION);
                }
                break;
            }
            default: {
                throw new IllegalArgumentException("Unknown shipment status: " + oldStatus);
            }
        }
    }
}

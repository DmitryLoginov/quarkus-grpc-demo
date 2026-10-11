package dev.ldv.shipment.domain;

public class ShipmentException extends RuntimeException {

    private final Category category;

    public ShipmentException(String message, Category category) {
        super(message);
        this.category = category;
    }

    public ShipmentException(String message, Category category, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }

    public enum Category {
        INVALID_INPUT,
        SHIPMENT_NOT_FOUND,
        DUPLICATE_TRACKING_NUMBER,
        INVALID_STATUS_TRANSITION
    }
}

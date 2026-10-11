package dev.ldv.shipment.grpc;

import dev.ldv.shipment.domain.ShipmentException;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.ExceptionHandler;
import io.quarkus.grpc.ExceptionHandlerProvider;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class GrpcExceptionHandlerProvider implements ExceptionHandlerProvider {

    @Override
    public <ReqT, RespT> ExceptionHandler<ReqT, RespT> createHandler(ServerCall.Listener<ReqT> listener,
                                                                     ServerCall<ReqT, RespT> serverCall, Metadata metadata) {
        return new GrpcExceptionHandler<>(listener, serverCall, metadata);
    }

    @Override
    public Throwable transform(Throwable t) {
        Log.warnf(t, "Error during gRPC call handling");

        if (t instanceof ShipmentException shipmentException) {
            return mapShipmentException(shipmentException);
        }

        if (t instanceof ConstraintViolationException constraintViolationException) {
            return mapConstraintViolationException(constraintViolationException);
        }

        return ExceptionHandlerProvider.toStatusException(t, true);
    }

    private StatusRuntimeException mapShipmentException(ShipmentException shipmentException) {
        return switch (shipmentException.getCategory()) {
            case INVALID_INPUT ->
                    new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription(shipmentException.getMessage()));
            case SHIPMENT_NOT_FOUND ->
                    new StatusRuntimeException(Status.NOT_FOUND.withDescription(shipmentException.getMessage()));
            case DUPLICATE_TRACKING_NUMBER ->
                    new StatusRuntimeException(Status.ALREADY_EXISTS.withDescription(shipmentException.getMessage()));
            case INVALID_STATUS_TRANSITION ->
                    new StatusRuntimeException(Status.FAILED_PRECONDITION.withDescription(shipmentException.getMessage()));
        };
    }

    private StatusRuntimeException mapConstraintViolationException(ConstraintViolationException constraintViolationException) {
        Map<String, String> violations = new TreeMap<>();

        constraintViolationException.getConstraintViolations().forEach(constraintViolation -> {
            Path propertyFullName = constraintViolation.getPropertyPath();
            String propertyName = getLastNodeName(propertyFullName);
            String errorMessage = constraintViolation.getMessage();

            violations.put(propertyName, errorMessage);
        });

        String errorMessage = "Input parameters are invalid: " + violations;

        return new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription(errorMessage));
    }

    private String getLastNodeName(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name;
    }

    private class GrpcExceptionHandler<A, B> extends ExceptionHandler<A, B> {

        public GrpcExceptionHandler(ServerCall.Listener<A> listener, ServerCall<A, B> call, Metadata metadata) {
            super(listener, call, metadata);
        }

        @Override
        protected void handleException(Throwable t, ServerCall<A, B> call, Metadata metadata) {
            Throwable transformed =
                    GrpcExceptionHandlerProvider.this.transform(t);

            StatusRuntimeException exception =
                    (StatusRuntimeException)
                            ExceptionHandlerProvider.toStatusException(
                                    transformed, true);

            Metadata trailers = exception.getTrailers() != null
                    ? exception.getTrailers()
                    : metadata;

            call.close(exception.getStatus(), trailers);
        }
    }
}

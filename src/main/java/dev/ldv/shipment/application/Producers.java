package dev.ldv.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;

@ApplicationScoped
public class Producers {

    @Produces
    @Singleton
    Clock clock() {
        return Clock.systemUTC();
    }
}

package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConversionPropertiesTest {

    @Test
    void workerThreadsClampsToAtLeastOne() {
        ConversionProperties properties = new ConversionProperties();

        properties.setWorkerThreads(0);

        assertEquals(1, properties.getWorkerThreads());
    }

    @Test
    void queueCapacityClampsToAtLeastOne() {
        ConversionProperties properties = new ConversionProperties();

        properties.setQueueCapacity(-10);

        assertEquals(1, properties.getQueueCapacity());
    }
}

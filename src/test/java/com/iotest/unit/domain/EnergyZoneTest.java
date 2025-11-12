package com.iotest.unit.domain;

import com.iotest.domain.model.EnergyCost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.iotest.domain.model.EnergyCost.HIGH;
import static com.iotest.domain.model.EnergyCost.LOW;
import static org.assertj.core.api.Assertions.assertThat;

public class EnergyZoneTest {

    @Test
    @DisplayName("Condiciones basicas de EnergyCost")
    void testEnergyCostBasicConditions(){
        // Pasar el tiempo como parámetro (NO usar currentEnergyZone que consulta internamente)
        long now = System.currentTimeMillis();
        EnergyCost.EnergyZone eCost = EnergyCost.energyZone(EnergyCost.TEST_CONTRACT_30S, now);

        assertThat(eCost.current()).isIn(HIGH, LOW);
        assertThat(eCost.current()).isNotEqualTo(eCost.next());
        assertThat(eCost.nextTS()).isGreaterThan(now);
    }
}


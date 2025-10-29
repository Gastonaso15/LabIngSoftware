package com.iotest.unit.domain;

import com.iotest.domain.model.EnergyCost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static uy.iiss.energy.EnergyCost.HIGH;
import static uy.iiss.energy.EnergyCost.LOW;

public class EnergyZoneTest {

    @Test
    @DisplayName("Condiciones basicas de EnergyCost")
    void testExtractSensorDataFromX.Json(){
        long now =  System.currentTimeMillis();

        EnergyCost.EnergyZone eCost = EnergyCost.currentEnergyZone(EnergyCost.TEST_CONTRACT_30S);

        assert(eCost.current()==HIGH || eCost.current()==LOW);
        assert(eCost.currebt() != eCost.next());
        assert(eCost.nextTS() > now);
    }
}

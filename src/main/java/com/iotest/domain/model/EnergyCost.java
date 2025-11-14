package com.iotest.domain.model;

/**
 * Informa Costo de la Energía y cuando cambia de uno costo a otro.
 */
public class EnergyCost {

    /**
     * Contrato para pruebas, cambia cada 30 segundos.
     */
    public final static String TEST_CONTRACT_30S = "testContract";

    /**
     * Tarifa baja - Llano
     */
    public final static int LOW = 0;

    /**
     * Tarifa alta - Punta
     */
    public final static int HIGH = 1;

    /**
     * Duración de la Zona para validar.
     * Para testContract: 30 segundos (cambia cada 30 segundos entre HIGH y LOW)
     */
    private final static long ZONE_DURATION = 1000 * 30; // 30 segundos en milisegundos

    /**
     * Indica el tipo de Tarifa actual. También indica cuando se va a activar una nueva tarifa y cuál va a ser.
     * </p>
     * Tarifas: 0 -> BAJA (tarifa económica), 1 -> ALTA (tarifa cara)
     *
     * @param current Tipo de Tarifa actual
     * @param next    Próxima Tarifa que va a activarse
     * @param nextTS  Cuando se va a activar la nueva tarifa como milisegundos desde January 1, 1970 UTC
     *                (misma unidad que System.currentTimeMillis())
     */
    public record EnergyZone(int current, int next, long nextTS) {
    }

    /**
     * Informa la tarifa actual y cuándo se cambia a la siguiente tarifa, para el instante actual.
     */
    public static EnergyZone currentEnergyZone(String contract) {
        return energyZone(contract, System.currentTimeMillis());
    }

    /**
     * Informa la tarifa actual y cuándo se cambia a la siguiente tarifa, para el tipo de contrato y ts
     * recibido como parámetro.
     */
    public static EnergyZone energyZone(String contract, long ts) {
        if (TEST_CONTRACT_30S.equals(contract)) {
            long base = ts / ZONE_DURATION;
            int zone = (int) (base % 2);
            int nextZone = (zone + 1) % 2;
            long nextZoneTS = (base + 1) * ZONE_DURATION;

            return new EnergyZone(zone, nextZone, nextZoneTS);
        } else {
            throw new IllegalArgumentException("Invalid contract value: " + contract);
        }
    }
}

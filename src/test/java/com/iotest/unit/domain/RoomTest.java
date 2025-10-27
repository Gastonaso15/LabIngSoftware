package com.iotest.unit.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import com.iotest.domain.model.Room;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Room - Tests Unitarios")
class RoomTest {
    private Room room;

    @BeforeEach
    void setUp{
        room = new Room (
                "living_room",
                "Living room",
                23.0,
                1200,
                "http://switch:8080"
        );
    }

    @Test
    @DisplayName("Debe indicar que necesita calefacción cuando temperatura está por debajo de la deseada")
    void testNeedsHeatingWhenBelowTarget(){
        // Arrange
        room.updateTemperature(18.0, LocalDateTime.now());
        //act
        boolean needsHeating = room.needsHeating();
        //Assert
        assertThat(needsHeating).isTrue();
    }

    @Test
    @DisplayName("No debe necesitar calefacción cuando está en temperatura objetivo")
    void testDoesNotNeedHeatingWhenAtTarget(){
        //arrange
        room.updateTemperature(23.0, LocalDateTime.now());
        //act
        boolean needsHeating = room.needsHeating();
        //assert
        assertThat(needsHeating).isFalse();

    }
}


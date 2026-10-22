package com.jizhi.videomid.eagleeye;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EagleEyeCommandsTest {

    @Test
    void directionUsesProtocolNames() {
        assertEquals("up", EagleEyeCommands.direction("up"));
        assertEquals("upleft", EagleEyeCommands.direction("left_up"));
        assertEquals("upright", EagleEyeCommands.direction("RIGHT_UP"));
        assertEquals("downleft", EagleEyeCommands.direction("left_down"));
        assertEquals("downright", EagleEyeCommands.direction("right_down"));
        assertNull(EagleEyeCommands.direction("stop"));
    }

    @Test
    void zoomAndFocusKeepProtocolValues() {
        assertEquals("zoom_in", EagleEyeCommands.zoom("in"));
        assertEquals("zoom_out", EagleEyeCommands.zoom("wide"));
        assertEquals("focus_near", EagleEyeCommands.focus("near"));
        assertEquals("focus_far", EagleEyeCommands.focus("focus_far"));
    }

    @Test
    void speedStaysInsideSubsystemRange() {
        assertEquals(1, EagleEyeCommands.speed(0));
        assertEquals(4, EagleEyeCommands.speed(4));
        assertEquals(7, EagleEyeCommands.speed(8));
    }

    @Test
    void terminalIdDefaultsToDeviceId() {
        EagleEyeProperties properties = new EagleEyeProperties();
        assertEquals("YY01", properties.terminalIdOf("YY01"));
        EagleEyeProperties.Terminal terminal = new EagleEyeProperties.Terminal();
        terminal.setDeviceId("cam-1");
        terminal.setTerminalId("YY02");
        properties.setTerminals(java.util.List.of(terminal));
        assertEquals("YY02", properties.terminalIdOf("cam-1"));
    }
}

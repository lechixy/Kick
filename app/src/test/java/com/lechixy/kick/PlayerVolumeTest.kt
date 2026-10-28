package com.lechixy.kick

import com.lechixy.kick.util.PlayerUtils
import org.junit.Assert.*
import org.junit.Test

class PlayerVolumeTest {
    @Test fun perceivedVolumeHasSilentAndUnityEndpointsAndIsMonotonic() {
        assertEquals(0f, PlayerUtils.perceivedVolumeToGain(-1f), 0f)
        assertEquals(1f, PlayerUtils.perceivedVolumeToGain(2f), 0f)
        val gains = (0..100).map { PlayerUtils.perceivedVolumeToGain(it / 100f) }
        assertTrue(gains.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun equalLoudnessRatiosHaveEqualDecibelSteps() {
        val half = PlayerUtils.perceivedVolumeToGain(0.5f)
        val quarter = PlayerUtils.perceivedVolumeToGain(0.25f)
        assertEquals(0.1f, half, 0.00001f)
        assertEquals(half, quarter / half, 0.00001f)
    }
}

package com.gregtechceu.gtceu.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GTMathTest {

    @Test
    void clampRespectsBothBounds() {
        assertEquals(0, GTMath.clamp(-1, 0, 10));
        assertEquals(5, GTMath.clamp(5, 0, 10));
        assertEquals(10, GTMath.clamp(11, 0, 10));
    }

    @Test
    void ceilDivRoundsTowardPositiveInfinity() {
        assertEquals(3, GTMath.ceilDiv(5, 2));
        assertEquals(-2, GTMath.ceilDiv(-5, 2));
        assertEquals(-2, GTMath.ceilDiv(5, -2));
        assertEquals(3, GTMath.ceilDiv(-5, -2));
    }

    @Test
    void saturatedCastClampsOutsideIntegerRange() {
        assertEquals(Integer.MAX_VALUE, GTMath.saturatedCast((long) Integer.MAX_VALUE + 1));
        assertEquals(Integer.MIN_VALUE, GTMath.saturatedCast((long) Integer.MIN_VALUE - 1));
        assertEquals(42, GTMath.saturatedCast(42));
    }

    @Test
    void splitPreservesEveryPartOfLargeValues() {
        assertArrayEquals(new int[] { Integer.MAX_VALUE, 42 }, GTMath.split((long) Integer.MAX_VALUE + 42));
        assertArrayEquals(new int[0], GTMath.split(0));
    }

    @Test
    void extremaHandleNegativeAndInfiniteValues() {
        assertEquals(-1.0F, GTMath.max(-3.0F, -2.0F, -1.0F));
        assertEquals(Float.NEGATIVE_INFINITY,
                GTMath.max(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY));
        assertEquals(Float.POSITIVE_INFINITY,
                GTMath.min(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
    }

    @Test
    void extremaRejectEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> GTMath.min());
        assertThrows(IllegalArgumentException.class, () -> GTMath.max());
    }
}

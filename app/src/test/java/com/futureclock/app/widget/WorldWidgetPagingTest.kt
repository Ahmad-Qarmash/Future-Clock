package com.futureclock.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldWidgetPagingTest {

    @Test
    fun pageCountAndWrappingFollowTheVisibleRowCapacity() {
        assertEquals(1, WorldWidgetPaging.pageCount(0, 3))
        assertEquals(1, WorldWidgetPaging.pageCount(3, 3))
        assertEquals(2, WorldWidgetPaging.pageCount(4, 3))
        assertEquals(3, WorldWidgetPaging.pageCount(13, 5))
        assertEquals(0, WorldWidgetPaging.nextPage(2, 13, 5, 1))
        assertEquals(2, WorldWidgetPaging.nextPage(0, 13, 5, -1))
    }

    @Test
    fun resizeNormalizesStalePageState() {
        assertEquals(0, WorldWidgetPaging.normalizedPage(4, 4, 4))
        assertEquals(2, WorldWidgetPaging.normalizedPage(5, 9, 4))
        assertEquals(0, WorldWidgetPaging.normalizedPage(-1, 0, 2))
    }
}

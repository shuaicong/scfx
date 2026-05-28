package com.scfx.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataSourceTest {
    @Test
    void testDataSourceCreation() {
        DataSource ds = new DataSource();
        ds.setCode("liangxin");
        ds.setName("粮信网");
        ds.setDescription("中国粮食网玉米晨报");
        ds.setEnabled(1);
        ds.setSortOrder(1);

        assertEquals("liangxin", ds.getCode());
        assertEquals("粮信网", ds.getName());
        assertEquals(1, ds.getEnabled());
    }

    @Test
    void testConfigJson() {
        DataSource ds = new DataSource();
        ds.setConfig("{\"loginUrl\": \"https://example.com/login\", \"authType\": \"cookie\"}");

        assertTrue(ds.getConfig().contains("loginUrl"));
        assertTrue(ds.getConfig().contains("cookie"));
    }
}
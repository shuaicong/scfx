package com.scfx.service;

import com.scfx.entity.DataSource;
import com.scfx.service.impl.DataSourceServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DataSourceServiceTest {
    @Autowired
    private DataSourceServiceImpl service;

    @Test
    void testGetAll() {
        List<DataSource> list = service.getAll();
        assertNotNull(list);
    }

    @Test
    void testGetAllEnabled() {
        List<DataSource> enabled = service.getAllEnabled();
        assertNotNull(enabled);
    }

    @Test
    void testGetByCode() {
        DataSource ds = service.getByCode("liangxin");
        if (ds != null) {
            assertEquals("liangxin", ds.getCode());
        }
    }
}
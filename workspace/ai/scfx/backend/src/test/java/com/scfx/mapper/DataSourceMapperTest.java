package com.scfx.mapper;

import com.scfx.entity.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DataSourceMapperTest {
    @Autowired
    private DataSourceMapper mapper;

    @Test
    void testFindAll() {
        List<DataSource> list = mapper.findAll();
        assertNotNull(list);
    }

    @Test
    void testFindAllEnabled() {
        List<DataSource> enabled = mapper.findAllEnabled();
        assertNotNull(enabled);
        for (DataSource ds : enabled) {
            assertEquals(1, ds.getEnabled());
        }
    }

    @Test
    void testFindByCode() {
        DataSource ds = mapper.findByCode("liangxin");
        if (ds != null) {
            assertEquals("liangxin", ds.getCode());
        }
    }
}
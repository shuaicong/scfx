package com.scfx.service;

import com.scfx.entity.DataSource;
import java.util.List;

public interface DataSourceService {
    List<DataSource> getAll();

    List<DataSource> getAllEnabled();

    DataSource getByCode(String code);

    DataSource create(DataSource dataSource);

    DataSource update(String code, DataSource dataSource);

    void delete(String code);

    void enable(String code);

    void disable(String code);

    void updateHeartbeat(String code);
}
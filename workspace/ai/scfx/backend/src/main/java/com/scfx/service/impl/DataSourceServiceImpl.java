package com.scfx.service.impl;

import com.scfx.entity.DataSource;
import com.scfx.mapper.DataSourceMapper;
import com.scfx.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {
    private final DataSourceMapper mapper;

    @Override
    public List<DataSource> getAll() {
        return mapper.findAll();
    }

    @Override
    public List<DataSource> getAllEnabled() {
        return mapper.findAllEnabled();
    }

    @Override
    public DataSource getByCode(String code) {
        return mapper.findByCode(code);
    }

    @Override
    @Transactional
    public DataSource create(DataSource dataSource) {
        mapper.insert(dataSource);
        return dataSource;
    }

    @Override
    @Transactional
    public DataSource update(String code, DataSource dataSource) {
        DataSource existing = mapper.findByCode(code);
        if (existing == null) {
            throw new RuntimeException("数据源不存在: " + code);
        }
        existing.setName(dataSource.getName());
        existing.setDescription(dataSource.getDescription());
        existing.setLogoUrl(dataSource.getLogoUrl());
        existing.setEnabled(dataSource.getEnabled());
        existing.setSortOrder(dataSource.getSortOrder());
        existing.setConfig(dataSource.getConfig());
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(String code) {
        DataSource existing = mapper.findByCode(code);
        if (existing != null) {
            mapper.deleteById(existing.getId());
        }
    }

    @Override
    @Transactional
    public void enable(String code) {
        mapper.updateEnabled(mapper.findByCode(code).getId(), 1);
    }

    @Override
    @Transactional
    public void disable(String code) {
        mapper.updateEnabled(mapper.findByCode(code).getId(), 0);
    }

    @Override
    public void updateHeartbeat(String code) {
        mapper.updateHeartbeat(code);
    }
}
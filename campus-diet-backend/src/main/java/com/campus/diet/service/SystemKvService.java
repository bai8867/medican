package com.campus.diet.service;

import com.campus.diet.entity.SystemKv;
import com.campus.diet.mapper.SystemKvMapper;
import org.springframework.stereotype.Service;

@Service
public class SystemKvService {

    private final SystemKvMapper systemKvMapper;

    public SystemKvService(SystemKvMapper systemKvMapper) {
        this.systemKvMapper = systemKvMapper;
    }

    public String get(String key, String defaultVal) {
        SystemKv row = systemKvMapper.selectById(key);
        return row == null ? defaultVal : row.getV();
    }

    public boolean flagOn(String key, boolean defaultOn) {
        String v = get(key, defaultOn ? "1" : "0");
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    public void upsert(String key, String value) {
        SystemKv kv = new SystemKv();
        kv.setK(key);
        kv.setV(value);
        if (systemKvMapper.selectById(key) == null) {
            systemKvMapper.insert(kv);
        } else {
            systemKvMapper.updateById(kv);
        }
    }
}

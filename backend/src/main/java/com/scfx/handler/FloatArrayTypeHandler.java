package com.scfx.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.nio.ByteBuffer;
import java.sql.*;

/**
 * float[] ↔ BINARY TypeHandler，支持动态维度。
 * <p>
 * DashScope 可视化向量：768 维 × 4 字节 = 3072 字节<br>
 * BGE-M3 检索向量：1024 维 × 4 字节 = 4096 字节<br>
 * 维度由实际数组长度决定，不硬编码固定值。
 */
@Slf4j
@MappedTypes(float[].class)
@MappedJdbcTypes(JdbcType.BINARY)
public class FloatArrayTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        ByteBuffer bb = ByteBuffer.allocate(parameter.length * 4);
        for (float v : parameter) {
            bb.putFloat(v);
        }
        ps.setBytes(i, bb.array());
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        byte[] bytes = rs.getBytes(columnName);
        return toFloatArray(bytes);
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        byte[] bytes = rs.getBytes(columnIndex);
        return toFloatArray(bytes);
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        byte[] bytes = cs.getBytes(columnIndex);
        return toFloatArray(bytes);
    }

    private float[] toFloatArray(byte[] bytes) {
        if (bytes == null) return null;
        int dims = bytes.length / 4;
        if (bytes.length % 4 != 0) {
            log.warn("向量数据长度不是 4 的倍数: {} 字节", bytes.length);
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        float[] result = new float[dims];
        for (int i = 0; i < dims; i++) {
            result[i] = bb.getFloat();
        }
        return result;
    }
}

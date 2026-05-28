package com.scfx.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.nio.ByteBuffer;
import java.sql.*;

/**
 * float[] ↔ VARBINARY(3072) TypeHandler
 * 固定 768 维 float，每维 4 字节，共 3072 字节
 */
@Slf4j
@MappedTypes(float[].class)
@MappedJdbcTypes(JdbcType.BINARY)
public class FloatArrayTypeHandler extends BaseTypeHandler<float[]> {

    private static final int EXPECTED_DIMS = 768;
    private static final int EXPECTED_BYTES = EXPECTED_DIMS * 4;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        if (parameter.length != EXPECTED_DIMS) {
            log.warn("向量维度异常: 期望 {} 维, 实际 {} 维", EXPECTED_DIMS, parameter.length);
        }
        ByteBuffer bb = ByteBuffer.allocate(EXPECTED_BYTES);
        for (int j = 0; j < EXPECTED_DIMS; j++) {
            bb.putFloat(j < parameter.length ? parameter[j] : 0f);
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
        if (bytes.length != EXPECTED_BYTES) {
            log.warn("向量数据长度异常: 期望 {} 字节, 实际 {} 字节", EXPECTED_BYTES, bytes.length);
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        int dims = bytes.length / 4;
        float[] result = new float[dims];
        for (int i = 0; i < dims; i++) {
            result[i] = bb.getFloat();
        }
        return result;
    }
}

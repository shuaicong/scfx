package com.scfx.service;

import com.scfx.entity.CollectorScriptVersion;
import java.util.List;

public interface CollectorScriptVersionService {
    List<CollectorScriptVersion> getVersions(String datasourceCode);

    CollectorScriptVersion getCurrentVersion(String datasourceCode);

    CollectorScriptVersion createVersion(String datasourceCode, String filePath, String fileMd5, int fileSize, String operator);

    CollectorScriptVersion uploadScript(String datasourceCode, byte[] content, String originalFilename, String operator);

    String getScriptContent(String datasourceCode, int version);

    boolean scriptExists(String datasourceCode);

    void rollback(String datasourceCode, int version);
}
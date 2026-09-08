package com.scoring.backend.common;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 容错 JSON 解析工具：脏数据一律降级为空对象，避免历史脏 JSON 打崩读接口。
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    public static JSONObject parseObject(String json) {
        if (StrUtil.isBlank(json)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception ex) {
            return new JSONObject();
        }
    }
}

package com.knowbrain.document.service.parser;

/**
 * 解析元数据 — 记录解析引擎、耗时、置信度等运行时信息
 *
 * @param engine     解析引擎标识（qwen-vl / poi / tika）
 * @param elapsedMs  解析耗时（毫秒）
 * @param confidence 置信度 0.0~1.0（1.0 = 纯文本提取，<1.0 = OCR 含不确定性）
 */
public record ParseMetadata(
        String engine,
        long elapsedMs,
        double confidence
) {

    /** 快速构造：置信度默认 1.0（纯文本解析） */
    public static ParseMetadata of(String engine, long elapsedMs) {
        return new ParseMetadata(engine, elapsedMs, 1.0);
    }

    /** 带置信度的构造（OCR 场景） */
    public static ParseMetadata of(String engine, long elapsedMs, double confidence) {
        return new ParseMetadata(engine, elapsedMs, confidence);
    }
}

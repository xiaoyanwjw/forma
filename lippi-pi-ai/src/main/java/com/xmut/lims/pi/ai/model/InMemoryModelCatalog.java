package com.xmut.lims.pi.ai.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 内存 Catalog 实现。
 */
public final class InMemoryModelCatalog implements ModelCatalog {

    public static final String DEFAULT_USE_CASE = "pi.default";

    /** 证书 OCR useCase（与 ai-models.yml / 业务配额同名）。 */
    public static final String CERTIFICATE_OCR_USE_CASE = "certificate-ocr";

    /** 检测标准纸质表 → ParamSchemeRoot（与 ai-models.yml 同名）。 */
    public static final String TEST_STANDARD_SCHEMA_USE_CASE = "test-standard-schema";

    /** 检测方法曲线纸质表 → ParamSchemeRoot（与 ai-models.yml 同名）。 */
    public static final String TEST_METHOD_CURVE_SCHEMA_USE_CASE = "test-method-curve-schema";

    /** 送样纸质委托登记 → 样品行 JSON（与 ai-models.yml 同名）。 */
    public static final String WALK_IN_IMPORT_USE_CASE = "walk-in-import";

    /** 现场填报仪器谱图 PDF 抽取（与 ai-models.yml / 业务配额同名）。 */
    public static final String INST_PDF_USE_CASE = "inst-pdf";


    private final Map<String, ModelDescriptor> byUseCase;

    public InMemoryModelCatalog(Map<String, ModelDescriptor> descriptors) {
        Map<String, ModelDescriptor> copy = new LinkedHashMap<>();
        if (descriptors != null) {
            copy.putAll(descriptors);
        }
        this.byUseCase = Collections.unmodifiableMap(copy);
    }

    /**
     * 默认：native tool calling 开启的 stub 用例。
     */
    public static InMemoryModelCatalog defaults() {
        ModelDescriptor def = ModelDescriptor.builder()
                .useCase(DEFAULT_USE_CASE)
                .provider("stub")
                .model("stub-echo")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true)
                .build();
        return new InMemoryModelCatalog(Collections.singletonMap(DEFAULT_USE_CASE, def));
    }

    /**
     * 默认 + {@code certificate-ocr}（MULTIMODAL）+ {@code test-standard-schema}（CHAT）；
     * 生产 Catalog / 单测 Fake 共用。
     */
    public static InMemoryModelCatalog defaultsWithCertificateOcr() {
        Map<String, ModelDescriptor> map = new LinkedHashMap<>();
        ModelDescriptor def = ModelDescriptor.builder()
                .useCase(DEFAULT_USE_CASE)
                .provider("stub")
                .model("stub-echo")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true)
                .build();
        map.put(DEFAULT_USE_CASE, def);
        map.put(CERTIFICATE_OCR_USE_CASE, certificateOcrDescriptor());
        map.put(TEST_STANDARD_SCHEMA_USE_CASE, testStandardSchemaDescriptor());
        map.put(TEST_METHOD_CURVE_SCHEMA_USE_CASE, testMethodCurveSchemaDescriptor());
        map.put(WALK_IN_IMPORT_USE_CASE, walkInImportDescriptor());
        map.put(INST_PDF_USE_CASE, instPdfDescriptor());
        return new InMemoryModelCatalog(map);
    }

    /** 与现网 ai-models.yml {@code certificate-ocr} 对齐的描述符。 */
    public static ModelDescriptor certificateOcrDescriptor() {
        return ModelDescriptor.builder()
                .useCase(CERTIFICATE_OCR_USE_CASE)
                .provider("dashscope")
                .model("qwen3-vl-plus")
                .modalities(Collections.singleton(ModelModality.MULTIMODAL))
                .supportsNativeToolCalling(false)
                .dailyQuotaPerTenant(0)
                .temperature(0.1)
                .maxTokens(1500)
                .maxAttempts(2)
                .cacheTtlSeconds(0)
                .build();
    }

    /** 与现网 ai-models.yml {@code test-standard-schema} 对齐的描述符。 */
    public static ModelDescriptor testStandardSchemaDescriptor() {
        return ModelDescriptor.builder()
                .useCase(TEST_STANDARD_SCHEMA_USE_CASE)
                .provider("deepseek")
                .model("deepseek-v4-flash")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true)
                .dailyQuotaPerTenant(0)
                .temperature(0.2)
                .maxTokens(16_000)
                // V4 默认 thinking=on 且与 content 共享 max_tokens，结构化 JSON 易截断
                .thinkingMode("disabled")
                .maxAttempts(1)
                .cacheTtlSeconds(0)
                .build();
    }

    /** 与现网 ai-models.yml {@code test-method-curve-schema} 对齐的描述符。 */
    public static ModelDescriptor testMethodCurveSchemaDescriptor() {
        return ModelDescriptor.builder()
                .useCase(TEST_METHOD_CURVE_SCHEMA_USE_CASE)
                .provider("deepseek")
                .model("deepseek-v4-flash")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true)
                .dailyQuotaPerTenant(0)
                .temperature(0.2)
                .maxTokens(16_000)
                .thinkingMode("disabled")
                .maxAttempts(1)
                .cacheTtlSeconds(0)
                .build();
    }

    /** 与现网 ai-models.yml {@code walk-in-import} 对齐的描述符。 */
    public static ModelDescriptor walkInImportDescriptor() {
        return ModelDescriptor.builder()
                .useCase(WALK_IN_IMPORT_USE_CASE)
                .provider("deepseek")
                .model("deepseek-v4-flash")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true)
                .dailyQuotaPerTenant(0)
                .temperature(0.2)
                .maxTokens(16_000)
                .thinkingMode("disabled")
                .maxAttempts(1)
                .cacheTtlSeconds(0)
                .build();
    }

    /** 与现网 ai-models.yml {@code inst-pdf} 对齐的描述符。 */
    public static ModelDescriptor instPdfDescriptor() {
        return ModelDescriptor.builder()
                .useCase(INST_PDF_USE_CASE)
                .provider("deepseek")
                .model("deepseek-v4-flash")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true)
                .dailyQuotaPerTenant(0)
                .temperature(0.1)
                .maxTokens(16_000)
                .thinkingMode("disabled")
                .maxAttempts(2)
                .cacheTtlSeconds(0)
                .build();
    }

    @Override
    public ModelDescriptor resolve(String useCase) {
        String key = useCase != null ? useCase : DEFAULT_USE_CASE;
        ModelDescriptor desc = byUseCase.get(key);
        if (desc == null) {
            throw new UnsupportedModelException(key);
        }
        return desc;
    }

    @Override
    public Set<String> registeredUseCases() {
        return byUseCase.keySet();
    }
}

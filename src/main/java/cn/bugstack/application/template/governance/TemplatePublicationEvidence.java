package cn.bugstack.application.template.governance;

import java.time.Instant;

/** 模板发布前使用样例数据完成真实渲染后产生的不可变证据。 */
public final class TemplatePublicationEvidence {
    private final String sampleDataSha256;
    private final String renderedSha256;
    private final long renderedSize;
    private final Instant validatedAt;

    public TemplatePublicationEvidence(String sampleDataSha256, String renderedSha256,
                                       long renderedSize, Instant validatedAt) {
        this.sampleDataSha256 = sampleDataSha256;
        this.renderedSha256 = renderedSha256;
        this.renderedSize = renderedSize;
        this.validatedAt = validatedAt;
    }

    public String getSampleDataSha256() { return sampleDataSha256; }
    public String getRenderedSha256() { return renderedSha256; }
    public long getRenderedSize() { return renderedSize; }
    public Instant getValidatedAt() { return validatedAt; }
}

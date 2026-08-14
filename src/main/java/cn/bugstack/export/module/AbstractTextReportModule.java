package cn.bugstack.export.module;

import cn.bugstack.export.document.ReportSectionBuilder;

import java.util.function.Function;

/**
 * 数据对象最终写入一段正文文本的模块父类。
 *
 * <p>泛型始终是业务对象 {@code T}，而不是 {@code String}。该父类只把对象中的一个文本
 * 字段适配为段落；表格、图片、子章节等复杂模块继续直接继承
 * {@link AbstractReportModule}。</p>
 *
 * @param <T> 模块业务对象类型
 */
public abstract class AbstractTextReportModule<T> extends AbstractReportModule<T> {

    private final ModuleDescriptor<T> descriptor;
    private final Function<T, String> textResolver;

    /** 创建一个对象型单段文本模块。 */
    protected AbstractTextReportModule(ModuleDescriptor<T> descriptor,
                                       Function<T, String> textResolver) {
        if (descriptor == null || textResolver == null) {
            throw new IllegalArgumentException("module descriptor and text resolver must not be null");
        }
        this.descriptor = descriptor;
        this.textResolver = textResolver;
    }

    @Override
    public final ModuleDescriptor<T> descriptor() {
        return descriptor;
    }

    @Override
    protected void composeContent(ReportSectionBuilder section, T data,
                                  ReportModuleContext context) {
        section.paragraph(resolveText(data));
    }

    /**
     * 提取并校验对象中的正文文本，子类扩展表格等内容时仍可复用。
     */
    protected final String resolveText(T data) {
        String text = textResolver.apply(data);
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("report module text must not be blank: " + descriptor.getCode());
        }
        return text;
    }
}

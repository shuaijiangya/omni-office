package cn.bugstack.office.docx;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.exception.DocxValidationException;
import cn.bugstack.office.docx.model.DocumentNode;
import cn.bugstack.office.docx.render.DocxRenderer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererBoundaryTest {

    @Test
    void saveValidatesBeforeRendering() {
        DocxDocument empty = DocxDocument.create();
        AtomicBoolean rendered = new AtomicBoolean(false);

        assertThrows(DocxValidationException.class,
                () -> empty.save(Path.of("target/empty.docx"), rendererThatSets(rendered)));

        assertTrue(!rendered.get());
    }

    @Test
    void saveDelegatesValidDocumentToRenderer() {
        DocxDocument document = DocxDocument.create()
                .section()
                .paragraph()
                .text("正文")
                .end()
                .end();
        AtomicBoolean rendered = new AtomicBoolean(false);

        document.save(Path.of("target/valid.docx"), rendererThatSets(rendered));

        assertTrue(rendered.get());
    }

    /**
     * 创建调用时会标记执行状态的测试渲染器。
     *
     * @param rendered 渲染状态标记
     * @return 测试渲染器
     */
    private DocxRenderer rendererThatSets(AtomicBoolean rendered) {
        return new DocxRenderer() {
            @Override
            public void render(DocumentNode document, Path outputPath) {
                rendered.set(true);
            }
        };
    }
}

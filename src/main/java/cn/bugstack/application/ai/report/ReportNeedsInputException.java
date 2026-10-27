package cn.bugstack.application.ai.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 某个标题缺少可核实的数据时，阻止生成并把待补充问题交还调用方。 */
public final class ReportNeedsInputException extends IllegalStateException {

    private final String sectionId;
    private final List<String> questions;

    public ReportNeedsInputException(String sectionId, List<String> questions) {
        super("report section needs input: " + sectionId + " - " + String.join("; ", questions));
        this.sectionId = sectionId;
        this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
    }

    public String getSectionId() { return sectionId; }

    public List<String> getQuestions() { return questions; }
}

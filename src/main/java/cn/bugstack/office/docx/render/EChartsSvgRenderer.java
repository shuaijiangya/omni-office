package cn.bugstack.office.docx.render;

import cn.bugstack.office.docx.model.ChartNode;
import cn.bugstack.office.docx.model.ChartSeriesNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 可选的 ECharts 服务端 SVG 渲染器。
 *
 * <p>只传输受控的图表数据，不执行模型提供的 JavaScript 或原始 ECharts option。
 * Node 和 npm 依赖仅在显式选择 ECHARTS_IMAGE 时需要。</p>
 */
public final class EChartsSvgRenderer {

    private static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 把图表节点渲染为静态 SVG 字节，供 Word 图片节点使用。
     *
     * @param chart 已通过文档校验的图表
     * @return SVG 文件内容
     */
    public byte[] render(ChartNode chart) {
        if (chart == null) throw new IllegalArgumentException("chart is required");
        Path script = Path.of(System.getProperty("omni.office.echarts.script",
                "tools/echarts/render.cjs")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("ECharts renderer script not found: " + script);
        }
        ObjectNode input = mapper.createObjectNode();
        input.put("chartType", chart.getChartType().name());
        input.put("stylePreset", chart.getStylePreset().name());
        input.put("title", chart.getTitle());
        input.put("width", chart.getWidthPoints() * 96D / 72D);
        input.put("height", chart.getHeightPoints() * 96D / 72D);
        input.put("legendVisible", chart.isLegendVisible());
        input.put("showValues", chart.isShowValues());
        input.put("showPercentages", chart.isShowPercentages());
        input.put("categoryAxisTitle", chart.getCategoryAxisTitle());
        input.put("valueAxisTitle", chart.getValueAxisTitle());
        ArrayNode categories = input.putArray("categories");
        chart.getCategories().forEach(categories::add);
        ArrayNode series = input.putArray("series");
        for (ChartSeriesNode item : chart.getSeries()) {
            ObjectNode entry = series.addObject();
            entry.put("name", item.getName());
            ArrayNode values = entry.putArray("values");
            item.getValues().forEach(values::add);
        }
        ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "echarts-svg-reader");
            thread.setDaemon(true);
            return thread;
        });
        Process process = null;
        try {
            process = new ProcessBuilder(System.getProperty("omni.office.node", "node"), script.toString())
                    .redirectErrorStream(true).start();
            Process active = process;
            Future<byte[]> response = reader.submit(() -> active.getInputStream().readNBytes(MAX_OUTPUT_BYTES + 1));
            byte[] request = mapper.writeValueAsBytes(input);
            if (request.length > 256 * 1024) throw new IllegalArgumentException("chart input is too large");
            process.getOutputStream().write(request);
            process.getOutputStream().close();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("ECharts rendering timed out");
            }
            byte[] output = response.get(5, TimeUnit.SECONDS);
            if (output.length > MAX_OUTPUT_BYTES || process.exitValue() != 0) {
                throw new IllegalStateException("ECharts rendering failed: "
                        + new String(output, 0, Math.min(output.length, 300), StandardCharsets.UTF_8));
            }
            if (!new String(output, 0, Math.min(output.length, 100), StandardCharsets.UTF_8)
                    .contains("<svg")) {
                throw new IllegalStateException("ECharts did not return SVG");
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("ECharts runtime unavailable; run npm ci --prefix tools/echarts", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ECharts rendering interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("ECharts rendering failed", e);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            reader.shutdownNow();
        }
    }
}

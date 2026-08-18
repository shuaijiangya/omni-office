package cn.bugstack.application.template.governance;

import cn.bugstack.protocol.template.DocumentTemplateSpecJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Files;
import java.nio.file.Path;

/** 模板草稿、提审和审批的最小运维 CLI。 */
public final class TemplateAdminMain {

    private TemplateAdminMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) usage();
        Path dataRoot = Path.of(args[0]).toAbsolutePath().normalize();
        String tenant = args[1];
        if (!tenant.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("invalid tenant id");
        FileDocumentTemplateCatalog catalog = new FileDocumentTemplateCatalog(
                dataRoot.resolve("tenants").resolve(tenant).resolve("templates"));
        Object result;
        switch (args[2]) {
            case "create":
                if (args.length != 5) usage();
                try (java.io.InputStream input = Files.newInputStream(Path.of(args[3]))) {
                    result = catalog.createDraft(new DocumentTemplateSpecJsonCodec().read(input), args[4]);
                }
                break;
            case "submit":
                if (args.length != 6) usage();
                result = catalog.submit(args[3], args[4], args[5]);
                break;
            case "approve":
                if (args.length < 6 || args.length > 7) usage();
                result = catalog.approve(args[3], args[4], args[5], args.length == 7 ? args[6] : null);
                break;
            case "reject":
                if (args.length != 7) usage();
                result = catalog.reject(args[3], args[4], args[5], args[6]);
                break;
            case "list":
                if (args.length != 3) usage();
                result = catalog.listRevisions();
                break;
            default:
                usage();
                return;
        }
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    private static void usage() {
        throw new IllegalArgumentException("Usage: <dataRoot> <tenant> "
                + "create <template.json> <actor> | submit <id> <version> <actor> | "
                + "approve <id> <version> <reviewer> [comment] | "
                + "reject <id> <version> <reviewer> <comment> | list");
    }
}

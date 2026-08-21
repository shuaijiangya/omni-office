package cn.bugstack.application.external.storage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3ArtifactObjectStorageTest {

    @Test
    void scopesObjectsToConfiguredTenantPrefix() throws Exception {
        Map<String, byte[]> objects = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, objects));
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        try (S3Client client = S3Client.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "secret")))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false).build())
                .build()) {
            S3ArtifactObjectStorage storage = new S3ArtifactObjectStorage(
                    client, "test-bucket", "root/tenants/tenant-a");
            byte[] content = "document".getBytes(StandardCharsets.UTF_8);
            storage.put("artifacts/id/content.bin", content, "application/octet-stream");

            assertTrue(objects.containsKey("root/tenants/tenant-a/artifacts/id/content.bin"));
            assertArrayEquals(content, storage.get("artifacts/id/content.bin"));
            assertEquals(java.util.List.of("artifacts/id/content.bin"), storage.list("artifacts/"));
            assertTrue(storage.delete("artifacts/id/content.bin"));
            assertFalse(storage.delete("artifacts/id/content.bin"));
        } finally {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange, Map<String, byte[]> objects) throws IOException {
        String rawPath = exchange.getRequestURI().getRawPath();
        String key = URLDecoder.decode(rawPath.replaceFirst("^/test-bucket/?", ""), StandardCharsets.UTF_8);
        String query = exchange.getRequestURI().getRawQuery();
        if ("GET".equals(exchange.getRequestMethod()) && query != null && query.contains("list-type=2")) {
            String prefix = queryValue(query, "prefix");
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    .append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">")
                    .append("<Name>test-bucket</Name><Prefix>").append(prefix)
                    .append("</Prefix><KeyCount>");
            long count = objects.keySet().stream().filter(item -> item.startsWith(prefix)).count();
            xml.append(count).append("</KeyCount><MaxKeys>1000</MaxKeys><IsTruncated>false</IsTruncated>");
            objects.keySet().stream().filter(item -> item.startsWith(prefix)).sorted()
                    .forEach(item -> xml.append("<Contents><Key>").append(item)
                            .append("</Key><Size>").append(objects.get(item).length)
                            .append("</Size><StorageClass>STANDARD</StorageClass></Contents>"));
            xml.append("</ListBucketResult>");
            send(exchange, 200, xml.toString().getBytes(StandardCharsets.UTF_8), "application/xml");
            return;
        }
        switch (exchange.getRequestMethod()) {
            case "PUT":
                objects.put(key, exchange.getRequestBody().readAllBytes());
                exchange.getResponseHeaders().set("ETag", "\"test\"");
                send(exchange, 200, new byte[0], null);
                return;
            case "GET":
                if (!objects.containsKey(key)) { send(exchange, 404, new byte[0], null); return; }
                send(exchange, 200, objects.get(key), "application/octet-stream");
                return;
            case "HEAD":
                if (key.isEmpty() || objects.containsKey(key)) send(exchange, 200, new byte[0], null);
                else send(exchange, 404, new byte[0], null);
                return;
            case "DELETE":
                objects.remove(key);
                send(exchange, 204, new byte[0], null);
                return;
            default:
                send(exchange, 405, new byte[0], null);
        }
    }

    private String queryValue(String query, String name) {
        for (String item : query.split("&")) {
            String[] sides = item.split("=", 2);
            if (sides[0].equals(name)) {
                return sides.length == 1 ? "" : URLDecoder.decode(sides[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private void send(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        if (contentType != null) exchange.getResponseHeaders().set("Content-Type", contentType);
        if ("HEAD".equals(exchange.getRequestMethod()) || status == 204) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}

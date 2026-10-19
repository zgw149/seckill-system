
package org.example.orderservice.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class OrderLoadTestRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Result(
            int index,
            String requestNo,
            long userId,
            int httpStatus,
            double latencyMs,
            String orderId,
            String orderNo,
            String businessStatus,
            String error,
            String responseBody
    ) {
    }

    public static void main(String[] args) throws Exception {

        if (args.length != 7) {
            printUsage();
            return;
        }

        String baseUrl = args[0].replaceAll("/+$", "");
        long productId = Long.parseLong(args[1]);
        int totalRequests = Integer.parseInt(args[2]);
        int concurrency = Integer.parseInt(args[3]);
        int quantity = Integer.parseInt(args[4]);
        String runId = args[5];
        String mode = args[6];

        validate(
                productId,
                totalRequests,
                concurrency,
                quantity,
                runId,
                mode
        );

        /*
         * 不同运行尽量使用不同的 userId。
         * 在 unique 模式下，每个请求都有不同用户。
         * 在 duplicate 模式下，所有请求使用同一个用户。
         */
        long userBase = 900_000_000L
                + (System.currentTimeMillis() % 1_000_000L) * 1_000L;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        ExecutorService pool =
                Executors.newFixedThreadPool(concurrency);

        CountDownLatch ready =
                new CountDownLatch(Math.min(totalRequests, concurrency));

        CountDownLatch start = new CountDownLatch(1);

        List<Future<Result>> futures = new ArrayList<>();

        String duplicateRequestNo = "P6-" + runId + "-DUP";

        System.out.println("========== Order Load Test ==========");
        System.out.println("baseUrl       = " + baseUrl);
        System.out.println("productId     = " + productId);
        System.out.println("totalRequests = " + totalRequests);
        System.out.println("concurrency   = " + concurrency);
        System.out.println("quantity      = " + quantity);
        System.out.println("runId         = " + runId);
        System.out.println("mode          = " + mode);
        System.out.println("======================================");

        try {
            for (int i = 1; i <= totalRequests; i++) {

                final int index = i;

                futures.add(pool.submit(() -> {

                    ready.countDown();

                    try {
                        if (!start.await(30, TimeUnit.SECONDS)) {
                            return new Result(
                                    index, "", 0, 0, 0,
                                    "", "", "", "等待启动超时", ""
                            );
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();

                        return new Result(
                                index, "", 0, 0, 0,
                                "", "", "", "线程被中断", ""
                        );
                    }

                    String requestNo;
                    long userId;

                    if ("duplicate".equals(mode)) {

                        requestNo = duplicateRequestNo;
                        userId = userBase;

                    } else {

                        requestNo = "P6-" + runId + "-" + index;
                        userId = userBase + index;
                    }

                    String json = """
                            {
                              "userId": %d,
                              "productId": %d,
                              "quantity": %d,
                              "requestNo": "%s"
                            }
                            """.formatted(
                            userId,
                            productId,
                            quantity,
                            requestNo
                    );

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/orders"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    long requestStart = System.nanoTime();

                    try {
                        HttpResponse<String> response = client.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                        double latencyMs =
                                (System.nanoTime() - requestStart)
                                        / 1_000_000.0;

                        String body = response.body() == null
                                ? ""
                                : response.body();

                        String orderId = jsonField(body, "orderId");
                        String orderNo = jsonField(body, "orderNo");
                        String businessStatus = jsonField(body, "status");

                        String error = "";

                        if (response.statusCode() < 200
                                || response.statusCode() >= 300) {

                            error = truncate(body, 500);
                        }

                        return new Result(
                                index,
                                requestNo,
                                userId,
                                response.statusCode(),
                                latencyMs,
                                orderId,
                                orderNo,
                                businessStatus,
                                error,
                                truncate(body, 1000)
                        );

                    } catch (Exception ex) {

                        double latencyMs =
                                (System.nanoTime() - requestStart)
                                        / 1_000_000.0;

                        return new Result(
                                index,
                                requestNo,
                                userId,
                                0,
                                latencyMs,
                                "",
                                "",
                                "",
                                ex.getClass().getSimpleName()
                                        + ": " + ex.getMessage(),
                                ""
                        );
                    }
                }));
            }

            if (!ready.await(30, TimeUnit.SECONDS)) {
                System.err.println(
                        "警告：部分工作线程未能及时就绪，仍将启动测试。"
                );
            }

            long wallStart = System.nanoTime();

            // 所有已就绪工作线程同时开始
            start.countDown();

            List<Result> results = new ArrayList<>();

            for (Future<Result> future : futures) {
                results.add(future.get());
            }

            long wallEnd = System.nanoTime();

            double wallSeconds =
                    (wallEnd - wallStart) / 1_000_000_000.0;

            Path csvPath = Path.of(
                    "phase6-" + runId + "-" + mode + ".csv"
            );

            writeCsv(csvPath, results);

            printMetrics(
                    results,
                    wallSeconds,
                    csvPath
            );

        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private static void validate(
            long productId,
            int totalRequests,
            int concurrency,
            int quantity,
            String runId,
            String mode) {

        if (productId <= 0
                || totalRequests <= 0
                || totalRequests > 5000
                || concurrency <= 0
                || concurrency > 200
                || quantity <= 0) {

            throw new IllegalArgumentException(
                    "参数非法：请求数最大 5000，并发数最大 200，"
                            + "商品 ID 和购买数量必须大于 0"
            );
        }

        if (!runId.matches("[A-Za-z0-9]{1,12}")) {
            throw new IllegalArgumentException(
                    "runId 只能包含 1～12 位字母或数字"
            );
        }

        if (!"unique".equals(mode)
                && !"duplicate".equals(mode)) {

            throw new IllegalArgumentException(
                    "mode 只能是 unique 或 duplicate"
            );
        }
    }

    private static String jsonField(
            String json,
            String field) {

        try {
            JsonNode root = MAPPER.readTree(json);

            if (root == null || !root.hasNonNull(field)) {
                return "";
            }

            return root.get(field).asText();

        } catch (IOException ex) {
            return "";
        }
    }

    private static void writeCsv(
            Path path,
            List<Result> results) throws IOException {

        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8)) {

            writer.write(
                    "index,requestNo,userId,httpStatus,latencyMs,"
                            + "orderId,orderNo,businessStatus,error,responseBody"
            );
            writer.newLine();

            for (Result r : results) {

                writer.write(String.join(",",
                        csv(String.valueOf(r.index())),
                        csv(r.requestNo()),
                        csv(String.valueOf(r.userId())),
                        csv(String.valueOf(r.httpStatus())),
                        csv(String.format(
                                Locale.ROOT,
                                "%.3f",
                                r.latencyMs()
                        )),
                        csv(r.orderId()),
                        csv(r.orderNo()),
                        csv(r.businessStatus()),
                        csv(r.error()),
                        csv(r.responseBody())
                ));

                writer.newLine();
            }
        }
    }

    private static String csv(String value) {

        if (value == null) {
            value = "";
        }

        return "\"" + value
                .replace("\"", "\"\"")
                .replace("\r", " ")
                .replace("\n", " ")
                + "\"";
    }

    private static void printMetrics(
            List<Result> results,
            double wallSeconds,
            Path csvPath) {

        long total = results.size();

        long http2xx = results.stream()
                .filter(r -> r.httpStatus() >= 200
                        && r.httpStatus() < 300)
                .count();

        long httpErrors = total - http2xx;

        long transportErrors = results.stream()
                .filter(r -> r.httpStatus() == 0)
                .count();

        List<Double> allLatencies = results.stream()
                .map(Result::latencyMs)
                .filter(v -> v > 0)
                .toList();

        List<Double> successfulLatencies = results.stream()
                .filter(r -> r.httpStatus() >= 200
                        && r.httpStatus() < 300)
                .map(Result::latencyMs)
                .toList();

        double average = allLatencies.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        Set<String> distinctOrderIds = new HashSet<>();

        for (Result r : results) {
            if (r.orderId() != null && !r.orderId().isBlank()) {
                distinctOrderIds.add(r.orderId());
            }
        }

        Map<String, Long> businessStatuses = new HashMap<>();

        for (Result r : results) {
            String status = r.businessStatus();

            if (status == null || status.isBlank()) {
                status = "NO_STATUS";
            }

            businessStatuses.merge(status, 1L, Long::sum);
        }

        System.out.println();
        System.out.println("========== Metrics ==========");
        System.out.printf(Locale.ROOT,
                "总请求数: %d%n", total);

        System.out.printf(Locale.ROOT,
                "HTTP 2xx: %d%n", http2xx);

        System.out.printf(Locale.ROOT,
                "HTTP 非 2xx / 异常: %d%n", httpErrors);

        System.out.printf(Locale.ROOT,
                "连接或传输异常: %d%n", transportErrors);

        System.out.printf(Locale.ROOT,
                "业务状态分布: %s%n", businessStatuses);

        System.out.printf(Locale.ROOT,
                "不同 orderId 数量: %d%n", distinctOrderIds.size());

        System.out.printf(Locale.ROOT,
                "测试墙钟时间: %.3f s%n", wallSeconds);

        System.out.printf(Locale.ROOT,
                "吞吐量: %.2f requests/s%n",
                wallSeconds > 0 ? total / wallSeconds : 0);

        System.out.printf(Locale.ROOT,
                "HTTP 2xx 吞吐量: %.2f requests/s%n",
                wallSeconds > 0 ? http2xx / wallSeconds : 0);

        System.out.printf(Locale.ROOT,
                "平均响应时间: %.2f ms%n", average);

        printPercentiles("全部响应", allLatencies);
        printPercentiles("HTTP 2xx 响应", successfulLatencies);

        System.out.println("逐请求 CSV: " + csvPath.toAbsolutePath());
        System.out.println("=============================");
    }

    private static void printPercentiles(
            String label,
            List<Double> latencies) {

        System.out.printf(
                Locale.ROOT,
                "%s p50=%.2f ms, p95=%.2f ms, p99=%.2f ms%n",
                label,
                percentile(latencies, 0.50),
                percentile(latencies, 0.95),
                percentile(latencies, 0.99)
        );
    }

    /**
     * 最近秩法（nearest-rank）计算百分位数。
     */
    private static double percentile(
            List<Double> values,
            double percentile) {

        if (values.isEmpty()) {
            return Double.NaN;
        }

        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());

        int index = (int) Math.ceil(percentile * sorted.size()) - 1;

        index = Math.max(0, Math.min(index, sorted.size() - 1));

        return sorted.get(index);
    }

    private static String truncate(
            String text,
            int maxLength) {

        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }

        return text.substring(0, maxLength);
    }

    private static void printUsage() {

        System.out.println("""
                参数格式：
                <baseUrl> <productId> <totalRequests> <concurrency>
                <quantity> <runId> <unique|duplicate>

                示例：
                http://localhost:8082 1 1000 100 1 A100 unique

                参数说明：
                unique    每个请求使用独立的 requestNo 和 userId
                duplicate 所有请求使用同一 requestNo、userId 和商品
                """);
    }
}

package org.example.orderservice;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderDifferentUserConcurrentTest {

    public static void main(String[] args) throws InterruptedException {
        int requestCount = 1000;

        ExecutorService executorService = Executors.newFixedThreadPool(100);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        for (int i = 0; i < requestCount; i++) {
            long userId = 100000L + i;

            executorService.submit(() -> {
                try {
                    startLatch.await();

                    String body = """
                            {
                              "userId": %d,
                              "productId": 1,
                              "quantity": 1
                            }
                            """.formatted(userId);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8082/orders"))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    HttpResponse<String> response = httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }

                    System.out.println(response.statusCode() + " -> " + response.body());

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        System.out.println("1000 个任务已提交，准备开始并发请求");

        startLatch.countDown();

        endLatch.await();

        executorService.shutdown();

        System.out.println("成功次数：" + successCount.get());
        System.out.println("失败次数：" + failCount.get());
    }
}
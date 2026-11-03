package it.gdr.diary.integration.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public final class ConcurrentCalls {

    private ConcurrentCalls() {
    }

    public static List<MvcTestResult> fireAtOnce(int count, IntFunction<MvcTestResult> call) {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcTestResult>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int callIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return call.apply(callIndex);
                }));
            }
            ready.await();
            start.countDown();
            List<MvcTestResult> results = new ArrayList<>();
            for (Future<MvcTestResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    public static List<Integer> statuses(List<MvcTestResult> results) {
        return results.stream().map(result -> result.getResponse().getStatus()).toList();
    }
}

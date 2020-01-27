/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package reactor.retry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.SocketException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import org.junit.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

public class RetryWithAsyncCallbackTest {

    private Queue<RetryContext<?>> retries = new ConcurrentLinkedQueue<>();

    @Test
    public void shouldTimeoutRetryWithVirtualTime() {
        // given
        final int minBackoff = 1;
        final int maxBackoff = 5;
        final int timeout = 10;

        // then
        StepVerifier.withVirtualTime(() ->
            Mono.<String>error(new RuntimeException("Something went wrong"))
                .retryWhen(RetryWithAsyncCallback.anyOf(Exception.class)
                    .exponentialBackoffWithJitter(Duration.ofSeconds(minBackoff), Duration.ofSeconds(maxBackoff))
                    .timeout(Duration.ofSeconds(timeout)))
                .subscribeOn(Schedulers.elastic()))
            .expectSubscription()
//				.expectNoEvent(Duration.ofSeconds(timeout))
            .thenAwait(Duration.ofSeconds(timeout))
            .expectError(RetryExhaustedException.class)
            .verify(Duration.ofSeconds(timeout));
    }

    @Test
    public void fluxRetryNoBackoff() {
        Flux<Integer> flux = Flux.concat(Flux.range(0, 2), Flux.error(new IOException()))
            .retryWhen(RetryWithAsyncCallback.any().noBackoff().retryMax(2).doOnRetry(onRetry()));

        StepVerifier.create(flux)
            .expectNext(0, 1, 0, 1, 0, 1)
            .verifyError(RetryExhaustedException.class);
        assertRetries(IOException.class, IOException.class);
        RetryTestUtils.assertDelays(retries, 0L, 0L);
    }

    @Test
    public void monoRetryNoBackoff() {
        Mono<?> mono = Mono.error(new IOException())
            .retryWhen(RetryWithAsyncCallback.any().noBackoff().retryMax(2).doOnRetry(onRetry()));

        StepVerifier.create(mono)
            .verifyError(RetryExhaustedException.class);
        assertRetries(IOException.class, IOException.class);
        RetryTestUtils.assertDelays(retries, 0L, 0L);
    }

    @Test
    public void fluxRetryFixedBackoff() {
        Flux<Integer> flux = Flux.concat(Flux.range(0, 2), Flux.error(new IOException()))
            .retryWhen(RetryWithAsyncCallback.any().fixedBackoff(Duration.ofMillis(500)).retryOnce().doOnRetry(onRetry()));

        StepVerifier.withVirtualTime(() -> flux)
            .expectNext(0, 1)
            .expectNoEvent(Duration.ofMillis(300))
            .thenAwait(Duration.ofMillis(300))
            .expectNext(0, 1)
            .verifyError(RetryExhaustedException.class);
        assertRetries(IOException.class);
        RetryTestUtils.assertDelays(retries, 500L);
    }

    @Test
    public void monoRetryFixedBackoff() {
        Mono<?> mono = Mono.error(new IOException())
            .retryWhen(RetryWithAsyncCallback.any().fixedBackoff(Duration.ofMillis(500)).retryOnce().doOnRetry(onRetry()));

        StepVerifier.withVirtualTime(() -> mono)
            .expectSubscription()
            .expectNoEvent(Duration.ofMillis(300))
            .thenAwait(Duration.ofMillis(300))
            .verifyError(RetryExhaustedException.class);

        assertRetries(IOException.class);
        RetryTestUtils.assertDelays(retries, 500L);
    }


    @Test
    public void fluxRetryExponentialBackoff() {
        Flux<Integer> flux = Flux.concat(Flux.range(0, 2), Flux.error(new IOException()))
            .retryWhen(RetryWithAsyncCallback.any()
                .exponentialBackoff(Duration.ofMillis(100), Duration.ofMillis(500))
                .timeout(Duration.ofMillis(1500))
                .doOnRetry(onRetry()));

        StepVerifier.create(flux)
            .expectNext(0, 1)
            .expectNoEvent(Duration.ofMillis(50))  // delay=100
            .expectNext(0, 1)
            .expectNoEvent(Duration.ofMillis(150)) // delay=200
            .expectNext(0, 1)
            .expectNoEvent(Duration.ofMillis(250)) // delay=400
            .expectNext(0, 1)
            .expectNoEvent(Duration.ofMillis(450)) // delay=500
            .expectNext(0, 1)
            .verifyErrorMatches(e -> isRetryExhausted(e, IOException.class));

        assertRetries(IOException.class, IOException.class, IOException.class, IOException.class);
        RetryTestUtils.assertDelays(retries, 100L, 200L, 400L, 500L);
    }
    @Test
    public void monoRetryExponentialBackoff() {
        Mono<?> mono = Mono.error(new IOException())
            .retryWhen(RetryWithAsyncCallback.any()
                .exponentialBackoff(Duration.ofMillis(100), Duration.ofMillis(500))
                .retryMax(4)
                .doOnRetry(onRetry()));

        StepVerifier.withVirtualTime(() -> mono)
            .expectSubscription()
            .thenAwait(Duration.ofMillis(100))
            .thenAwait(Duration.ofMillis(200))
            .thenAwait(Duration.ofMillis(400))
            .thenAwait(Duration.ofMillis(500))
            .verifyError(RetryExhaustedException.class);

        assertRetries(IOException.class, IOException.class, IOException.class, IOException.class);
        RetryTestUtils.assertDelays(retries, 100L, 200L, 400L, 500L);
    }

    @Test
    public void fluxRetryRandomBackoff() {
        Flux<Integer> flux = Flux.concat(Flux.range(0, 2), Flux.error(new IOException()))
            .retryWhen(RetryWithAsyncCallback.any()
                .randomBackoff(Duration.ofMillis(100), Duration.ofMillis(2000))
                .retryMax(4)
                .doOnRetry(onRetry()));

        StepVerifier.create(flux)
            .expectNext(0, 1, 0, 1, 0, 1, 0, 1, 0, 1)
            .verifyErrorMatches(e -> isRetryExhausted(e, IOException.class));

        assertRetries(IOException.class, IOException.class, IOException.class, IOException.class);
        RetryTestUtils.assertRandomDelays(retries, 100, 2000);
    }

    @Test
    public void monoRetryRandomBackoff() {
        Mono<?> mono = Mono.error(new IOException())
            .retryWhen(RetryWithAsyncCallback.any()
                .randomBackoff(Duration.ofMillis(100), Duration.ofMillis(2000))
                .retryMax(4)
                .doOnRetry(onRetry()));

        StepVerifier.withVirtualTime(() -> mono)
            .expectSubscription()
            .thenAwait(Duration.ofMillis(100))
            .thenAwait(Duration.ofMillis(2000))
            .thenAwait(Duration.ofMillis(2000))
            .thenAwait(Duration.ofMillis(2000))
            .verifyError(RetryExhaustedException.class);

        assertRetries(IOException.class, IOException.class, IOException.class, IOException.class);
        RetryTestUtils.assertRandomDelays(retries, 100, 2000);
    }


    @Test
    public void fluxRetriableExceptions() {
        Flux<Integer> flux = Flux.concat(Flux.range(0, 2), Flux.error(new SocketException()))
            .retryWhen(RetryWithAsyncCallback.anyOf(IOException.class).retryOnce().doOnRetry(onRetry()));

        StepVerifier.create(flux)
            .expectNext(0, 1, 0, 1)
            .verifyErrorMatches(e -> isRetryExhausted(e, SocketException.class));

        Flux<Integer> nonRetriable = Flux.concat(Flux.range(0, 2), Flux.error(new RuntimeException()))
            .retryWhen(RetryWithAsyncCallback.anyOf(IOException.class).retryOnce().doOnRetry(onRetry()));
        StepVerifier.create(nonRetriable)
            .expectNext(0, 1)
            .verifyError(RuntimeException.class);

    }

    @Test
    public void fluxNonRetriableExceptions() {

        Retry<?> retry = RetryWithAsyncCallback.allBut(RuntimeException.class).retryOnce().doOnRetry(onRetry());
        Flux<Integer> flux = Flux.concat(Flux.range(0, 2), Flux.error(new IllegalStateException())).retryWhen(retry);

        StepVerifier.create(flux)
            .expectNext(0, 1)
            .verifyError(IllegalStateException.class);


        Flux<Integer> retriable = Flux.concat(Flux.range(0, 2), Flux.error(new SocketException())).retryWhen(retry);
        StepVerifier.create(retriable)
            .expectNext(0, 1, 0, 1)
            .verifyErrorMatches(e -> isRetryExhausted(e, SocketException.class));
    }

    @Test
    public void fluxRetryAnyException() {
        Retry<?> retry = RetryWithAsyncCallback.any().retryOnce().doOnRetry(onRetry());

        Flux<Integer> flux = Flux.concat(Flux.range(0, 2), Flux.error(new SocketException())).retryWhen(retry);
        StepVerifier.create(flux)
            .expectNext(0, 1, 0, 1)
            .verifyErrorMatches(e -> isRetryExhausted(e, SocketException.class));

        Flux<Integer> flux2 = Flux.concat(Flux.range(0, 2), Flux.error(new RuntimeException())).retryWhen(retry);
        StepVerifier.create(flux2)
            .expectNext(0, 1, 0, 1)
            .verifyErrorMatches(e -> isRetryExhausted(e, RuntimeException.class));

    }

    @Test
    public void fluxRetryOnPredicate() {
        Flux<Integer> flux = Flux.concat(Flux.range(0, 2), Flux.error(new SocketException()))
            .retryWhen(RetryWithAsyncCallback.onlyIf(context -> context.iteration() < 3).doOnRetry(onRetry()));

        StepVerifier.create(flux)
            .expectNext(0, 1, 0, 1, 0, 1)
            .verifyError(SocketException.class);
    }


    @Test
    public void doOnRetry() {
        Semaphore semaphore = new Semaphore(0);
        Retry<?> retry = RetryWithAsyncCallback.any()
            .retryOnce()
            .fixedBackoff(Duration.ofMillis(500))
            .doOnRetry(context -> semaphore.release());

        StepVerifier.withVirtualTime(() -> Flux.range(0, 2).concatWith(Mono.error(new SocketException())).retryWhen(retry))
            .expectNext(0, 1)
            .then(semaphore::acquireUninterruptibly)
            .expectNoEvent(Duration.ofMillis(400))
            .thenAwait(Duration.ofMillis(200))
            .expectNext(0, 1)
            .verifyErrorMatches(e -> isRetryExhausted(e, SocketException.class));

        StepVerifier.withVirtualTime(() -> Mono.error(new SocketException()).retryWhen(retry.noBackoff()))
            .then(semaphore::acquireUninterruptibly)
            .verifyErrorMatches(e -> isRetryExhausted(e, SocketException.class));
    }

    @Test
    public void onRetryWithMono() {
        Semaphore semaphore = new Semaphore(0);
        Retry<?> retry = RetryWithAsyncCallback.any()
            .retryOnce()
            .fixedBackoff(Duration.ofMillis(500))
            .onRetryWithMono(context -> Mono.fromCallable(() -> { semaphore.release(); return 0; }));

        StepVerifier.withVirtualTime(() -> Flux.range(0, 2).concatWith(Mono.error(new SocketException())).retryWhen(retry))
            .expectNext(0, 1)
            .then(semaphore::acquireUninterruptibly)
            .expectNoEvent(Duration.ofMillis(400))
            .thenAwait(Duration.ofMillis(200))
            .expectNext(0, 1)
            .verifyErrorMatches(e -> isRetryExhausted(e, SocketException.class));

        StepVerifier.withVirtualTime(() -> Mono.error(new SocketException()).retryWhen(retry.noBackoff()))
            .then(semaphore::acquireUninterruptibly)
            .verifyErrorMatches(e -> isRetryExhausted(e, SocketException.class));
    }

    @Test
    public void retryApplicationContext() {
        class AppContext {
            boolean needsRollback;
            void rollback() {
                needsRollback = false;
            }
            void run() {
                assertFalse("Rollback not performed", needsRollback);
                needsRollback = true;
            }
        }
        AppContext appContext = new AppContext();
        Retry<?> retry = RetryWithAsyncCallback.<AppContext>any().withApplicationContext(appContext)
            .retryMax(2)
            .doOnRetry(context -> {
                AppContext ac = context.applicationContext();
                assertNotNull("Application context not propagated", ac);
                ac.rollback();
            });

        StepVerifier.withVirtualTime(() -> Mono.error(new RuntimeException()).doOnNext(i -> appContext.run()).retryWhen(retry))
            .verifyErrorMatches(e -> isRetryExhausted(e, RuntimeException.class));

    }

    @Test
    public void fluxRetryCompose() {
        Retry<?> retry = RetryWithAsyncCallback.any().noBackoff().retryMax(2).doOnRetry(this.onRetry());
        Flux<Integer> flux = Flux.concat(Flux.range(0, 2), Flux.error(new IOException())).as(retry::apply);

        StepVerifier.create(flux)
            .expectNext(0, 1, 0, 1, 0, 1)
            .verifyError(RetryExhaustedException.class);
        assertRetries(IOException.class, IOException.class);
    }

    @Test
    public void monoRetryCompose() {
        Retry<?> retry = RetryWithAsyncCallback.any().noBackoff().retryMax(2).doOnRetry(this.onRetry());
        Flux<?> flux = Mono.error(new IOException()).as(retry::apply);

        StepVerifier.create(flux)
            .verifyError(RetryExhaustedException.class);
        assertRetries(IOException.class, IOException.class);
    }

    @Test
    public void functionReuseInParallel() throws Exception {
        int retryCount = 19;
        int range = 100;
        Integer[] values = new Integer[(retryCount + 1) * range];
        for (int i = 0; i <= retryCount; i++) {
            for (int j = 1; j <= range; j++)
                values[i * range + j - 1] = j;
        }
        RetryTestUtils.testReuseInParallel(2, 20,
            backoff -> RetryWithAsyncCallback.<Integer>any().retryMax(19).backoff(backoff),
            retryFunc -> StepVerifier.create(Flux.range(1, range).concatWith(Mono.error(new SocketException())).retryWhen(retryFunc))
                .expectNext(values)
                .verifyErrorMatches(e -> isRetryExhausted(e, SocketException.class)));
    }

    Consumer<? super RetryContext<?>> onRetry() {
        return context -> retries.add(context);
    }

    @SafeVarargs
    private final void assertRetries(Class<? extends Throwable>... exceptions) {
        assertEquals(exceptions.length, retries.size());
        int index = 0;
        for (Iterator<RetryContext<?>> it = retries.iterator(); it.hasNext(); ) {
            RetryContext<?> retryContext = it.next();
            assertEquals(index + 1, retryContext.iteration());
            assertEquals(exceptions[index], retryContext.exception().getClass());
            index++;
        }
    }

    static boolean isRetryExhausted(Throwable e, Class<? extends Throwable> cause) {
        return e instanceof RetryExhaustedException && cause.isInstance(e.getCause());
    }

    @Test
    public void retryToString() {
        System.out.println(RetryWithAsyncCallback.any().noBackoff().retryMax(2).toString());
    }
}

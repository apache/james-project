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

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * This class is a copy of reactor.retry.DefaultRetry.
 * Its goal is to provide a way to execute an async action before retrying.
 * To do so it provides a retryWithMono method which is the async equivalent of the synchronous method doOnRetry.
 *
 * This is a temporary solution as this new requirement has been exposed in an issue in the reactor project.
 * see : https://github.com/reactor/reactor-addons/issues/220
 *
 */
public class RetryWithAsyncCallback<T> extends AbstractRetry<T, Throwable> implements Retry<T> {

    static final Logger log = Loggers.getLogger(RetryWithAsyncCallback.class);
    static final Consumer<? super RetryContext<?>> NOOP_ON_RETRY = r -> { };
    static final Function<? super RetryContext<?>, Mono<?>> NOOP_ON_RETRY_MONO = r -> Mono.empty();

    /**
     * Returns a retry function that retries any exception, once.
     * More constraints may be added using {@link #retryMax(long)} or {@link #timeout(Duration)}.
     *
     * @return retry function that retries on any exception
     */
    public static <T> RetryWithAsyncCallback<T> any() {
        return RetryWithAsyncCallback.<T>create(context -> true);
    }

    /**
     * Returns a retry function that retries errors resulting from any of the
     * specified exceptions, once.
     * More constraints may be added using {@link #retryMax(long)}
     * or {@link #timeout(Duration)}.
     *
     * @param retriableExceptions Exceptions that may be retried
     * @return retry function that retries indefinitely, only for specified exceptions
     */
    @SafeVarargs
    public static <T> RetryWithAsyncCallback<T> anyOf(Class<? extends Throwable>... retriableExceptions) {
        Predicate<? super RetryContext<T>> predicate = context -> {
            Throwable exception = context.exception();
            if (exception == null) {
                return true;
            }
            for (Class<? extends Throwable> clazz : retriableExceptions) {
                if (clazz.isInstance(exception)) {
                    return true;
                }
            }
            return false;
        };
        return RetryWithAsyncCallback.<T>create(predicate);
    }

    /**
     * Returns a retry function that retries errors resulting from all exceptions except
     * the specified non-retriable exceptions, once.
     * More constraints may be added using
     * {@link #retryMax(long)} or {@link #timeout(Duration)}.
     *
     * @param nonRetriableExceptions exceptions that may not be retried
     * @return retry function that retries all exceptions except the specified non-retriable exceptions.
     */
    @SafeVarargs
    public static <T> RetryWithAsyncCallback<T> allBut(final Class<? extends Throwable>... nonRetriableExceptions) {
        Predicate<? super RetryContext<T>> predicate = context -> {
            Throwable exception = context.exception();
            if (exception == null) {
                return true;
            }
            for (Class<? extends Throwable> clazz : nonRetriableExceptions) {
                if (clazz.isInstance(exception)) {
                    return false;
                }
            }
            return true;
        };
        return RetryWithAsyncCallback.<T>create(predicate);
    }

    /**
     * Retry function that retries only if the predicate returns true, with no limit to
     * the number of attempts.
     * @param predicate Predicate that determines if next retry is performed
     * @return Retry function with predicate
     */
    public static <T> RetryWithAsyncCallback<T> onlyIf(Predicate<? super RetryContext<T>> predicate) {
        return RetryWithAsyncCallback.create(predicate).retryMax(Long.MAX_VALUE);
    }

    public static <T> RetryWithAsyncCallback<T> create(Predicate<? super RetryContext<T>> retryPredicate) {
        return new RetryWithAsyncCallback<T>(retryPredicate,
            Long.MAX_VALUE,
            null,
            Backoff.zero(),
            Jitter.noJitter(),
            null,
            NOOP_ON_RETRY,
            NOOP_ON_RETRY_MONO,
            (T) null);
    }

    final Predicate<? super RetryContext<T>> retryPredicate;
    final Consumer<? super RetryContext<T>> onRetry;
    final Function<? super RetryContext<T>, Mono<?>> onRetryMono;

    RetryWithAsyncCallback(Predicate<? super RetryContext<T>> retryPredicate,
                           long maxIterations,
                           Duration timeout,
                           Backoff backoff,
                           Jitter jitter,
                           Scheduler backoffScheduler,
                           final Consumer<? super RetryContext<T>> onRetry,
                           Function<? super RetryContext<T>, Mono<?>> onRetryMono,
                           T applicationContext) {
        super(maxIterations, timeout, backoff, jitter, backoffScheduler, applicationContext);
        this.retryPredicate = retryPredicate;
        this.onRetry = onRetry;
        this.onRetryMono = onRetryMono;
    }

    @Override
    public RetryWithAsyncCallback<T> fixedBackoff(Duration backoffInterval) {
        return backoff(Backoff.fixed(backoffInterval));
    }

    @Override
    public RetryWithAsyncCallback<T> noBackoff() {
        return backoff(Backoff.zero());
    }

    @Override
    public RetryWithAsyncCallback<T> exponentialBackoff(Duration firstBackoff, Duration maxBackoff) {
        return backoff(Backoff.exponential(firstBackoff, maxBackoff, 2, false));
    }

    @Override
    public RetryWithAsyncCallback<T> exponentialBackoffWithJitter(Duration firstBackoff, Duration maxBackoff) {
        return backoff(Backoff.exponential(firstBackoff, maxBackoff, 2, false)).jitter(Jitter.random());
    }

    @Override
    public RetryWithAsyncCallback<T> randomBackoff(Duration firstBackoff, Duration maxBackoff) {
        return backoff(Backoff.exponential(firstBackoff, maxBackoff, 3, true)).jitter(Jitter.random());
    }

    @Override
    public RetryWithAsyncCallback<T> withApplicationContext(T applicationContext) {
        return new RetryWithAsyncCallback<>(retryPredicate, maxIterations, timeout,
            backoff, jitter, backoffScheduler, onRetry, onRetryMono, applicationContext);
    }

    @Override
    public RetryWithAsyncCallback<T> doOnRetry(Consumer<? super RetryContext<T>> onRetry) {
        return new RetryWithAsyncCallback<>(retryPredicate, maxIterations, timeout,
            backoff, jitter, backoffScheduler, onRetry, onRetryMono, applicationContext);
    }

    public RetryWithAsyncCallback<T> onRetryWithMono(Function<? super RetryContext<T>, Mono<?>> onRetryMono) {
        return new RetryWithAsyncCallback<>(retryPredicate, maxIterations, timeout,
            backoff, jitter, backoffScheduler, onRetry, onRetryMono, applicationContext);
    }

    @Override
    public RetryWithAsyncCallback<T> retryOnce() {
        return retryMax(1);
    }

    @Override
    public RetryWithAsyncCallback<T> retryMax(long maxIterations) {
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations should be >= 0");
        }
        return new RetryWithAsyncCallback<>(retryPredicate, maxIterations, timeout,
            backoff, jitter, backoffScheduler, onRetry, onRetryMono, applicationContext);
    }

    @Override
    public RetryWithAsyncCallback<T> timeout(Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout should be >= 0");
        }
        return new RetryWithAsyncCallback<>(retryPredicate, maxIterations, timeout,
            backoff, jitter, backoffScheduler, onRetry, onRetryMono, applicationContext);
    }

    @Override
    public RetryWithAsyncCallback<T> backoff(Backoff backoff) {
        return new RetryWithAsyncCallback<>(retryPredicate, maxIterations, timeout,
            backoff, jitter, backoffScheduler, onRetry, onRetryMono, applicationContext);
    }

    @Override
    public RetryWithAsyncCallback<T> jitter(Jitter jitter) {
        return new RetryWithAsyncCallback<>(retryPredicate, maxIterations, timeout,
            backoff, jitter, backoffScheduler, onRetry, onRetryMono, applicationContext);
    }

    @Override
    public RetryWithAsyncCallback<T> withBackoffScheduler(Scheduler scheduler) {
        return new RetryWithAsyncCallback<>(retryPredicate, maxIterations, timeout,
            backoff, jitter, scheduler, onRetry, onRetryMono, applicationContext);
    }

    @Override
    public Publisher<Long> apply(Flux<Throwable> errors) {
        Instant timeoutInstant = calculateTimeout();
        DefaultContext<T> context = new DefaultContext<>(applicationContext, 0L, null, null);
        return errors.index()
            .concatMap(tuple -> retry(tuple.getT2(), tuple.getT1() + 1L, timeoutInstant, context));
    }

    Publisher<Long> retry(Throwable e, long iteration, Instant timeoutInstant, DefaultContext<T> context) {
        DefaultContext<T> tmpContext = new DefaultContext<>(applicationContext, iteration, context.lastBackoff, e);
        BackoffDelay nextBackoff = calculateBackoff(tmpContext, timeoutInstant);
        DefaultContext<T> retryContext = new DefaultContext<T>(applicationContext, iteration, nextBackoff, e);
        context.lastBackoff = nextBackoff;

        if (!retryPredicate.test(retryContext)) {
            log.debug("Stopping retries since predicate returned false, retry context: {}", retryContext);
            return Mono.error(e);
        } else if (nextBackoff == RETRY_EXHAUSTED) {
            log.debug("Retries exhausted, retry context: {}", retryContext);
            return Mono.error(new RetryExhaustedException(e));
        } else {
            log.debug("Scheduling retry attempt, retry context: {}", retryContext);
            onRetry.accept(retryContext);
            return onRetryMono.apply(retryContext)
                .then(Mono.from(retryMono(nextBackoff.delay())));
        }
    }

    @Override
    public String toString() {
        return "Retry{max=" + this.maxIterations + ",backoff=" + backoff + ",jitter=" +
            jitter + "}";
    }
}

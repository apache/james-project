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

package org.apache.james.backends.rabbitmq;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import javax.annotation.PreDestroy;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ConsumerShutdownSignalCallback;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.ReturnCallback;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;
import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;
import reactor.pool.PooledRef;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ChannelPool;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

public class ReactorRabbitMQChannelPool implements ChannelPool, Startable {

    private static class SelectOnceChannel implements Channel {
        private final Channel delegate;
        private final AtomicBoolean confirmSelected = new AtomicBoolean(false);

        private SelectOnceChannel(Channel delegate) {
            this.delegate = delegate;
        }

        @Override
        public AMQP.Confirm.SelectOk confirmSelect() throws IOException {
            if (!confirmSelected.getAndSet(true)) {
                return delegate.confirmSelect();
            }
            return new AMQP.Confirm.SelectOk.Builder().build();
        }

        @Override
        public int getChannelNumber() {
            return delegate.getChannelNumber();
        }

        @Override
        public Connection getConnection() {
            return delegate.getConnection();
        }

        @Override
        public void close() throws IOException, TimeoutException {
            delegate.close();
        }

        @Override
        public void close(int closeCode, String closeMessage) throws IOException, TimeoutException {
            // https://www.rabbitmq.com/amqp-0-9-1-reference.html#domain.reply-code
            if (closeCode >= 300) {
                LOGGER.warn("Closing channel {} code:{} message:'{}'", getChannelNumber(), closeCode, closeMessage);
            }
            delegate.close(closeCode, closeMessage);
        }

        @Override
        public void abort() throws IOException {
            delegate.abort();
        }

        @Override
        public void abort(int closeCode, String closeMessage) throws IOException {
            // https://www.rabbitmq.com/amqp-0-9-1-reference.html#domain.reply-code
            if (closeCode >= 300) {
                LOGGER.warn("Closing channel {} code:{} message:'{}'", getChannelNumber(), closeCode, closeMessage);
            }
            delegate.abort(closeCode, closeMessage);
        }

        @Override
        public void addReturnListener(ReturnListener listener) {
            delegate.removeReturnListener(listener);
        }

        @Override
        public ReturnListener addReturnListener(ReturnCallback returnCallback) {
            return delegate.addReturnListener(returnCallback);
        }

        @Override
        public boolean removeReturnListener(ReturnListener listener) {
            return delegate.removeReturnListener(listener);
        }

        @Override
        public void clearReturnListeners() {
            delegate.clearReturnListeners();
        }

        @Override
        public void addConfirmListener(ConfirmListener listener) {
            delegate.addConfirmListener(listener);
        }

        @Override
        public ConfirmListener addConfirmListener(ConfirmCallback ackCallback, ConfirmCallback nackCallback) {
            return delegate.addConfirmListener(ackCallback, nackCallback);
        }

        @Override
        public boolean removeConfirmListener(ConfirmListener listener) {
            return delegate.removeConfirmListener(listener);
        }

        @Override
        public void clearConfirmListeners() {
            delegate.clearConfirmListeners();
        }

        @Override
        public Consumer getDefaultConsumer() {
            return delegate.getDefaultConsumer();
        }

        @Override
        public void setDefaultConsumer(Consumer consumer) {
            delegate.setDefaultConsumer(consumer);
        }

        @Override
        public void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException {
            delegate.basicQos(prefetchSize, prefetchCount, global);
        }

        @Override
        public void basicQos(int prefetchCount, boolean global) throws IOException {
            delegate.basicQos(prefetchCount, global);
        }

        @Override
        public void basicQos(int prefetchCount) throws IOException {
            delegate.basicQos(prefetchCount);
        }

        @Override
        public void basicPublish(String exchange, String routingKey, AMQP.BasicProperties props, byte[] body) throws IOException {
            delegate.basicPublish(exchange, routingKey, props, body);
        }

        @Override
        public void basicPublish(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body) throws IOException {
            delegate.basicPublish(exchange, routingKey, mandatory, props, body);

        }

        @Override
        public void basicPublish(String exchange, String routingKey, boolean mandatory, boolean immediate, AMQP.BasicProperties props, byte[] body) throws IOException {
            delegate.basicPublish(exchange, routingKey, mandatory, immediate, props, body);
        }

        @Override
        public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type) throws IOException {
            return delegate.exchangeDeclare(exchange, type);
        }

        @Override
        public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type) throws IOException {
            return delegate.exchangeDeclare(exchange, type);
        }

        @Override
        public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable) throws IOException {
            return delegate.exchangeDeclare(exchange, type, durable);
        }

        @Override
        public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable) throws IOException {
            return delegate.exchangeDeclare(exchange, type, durable);
        }

        @Override
        public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException {
            return delegate.exchangeDeclare(exchange, type, durable, autoDelete, arguments);
        }

        @Override
        public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException {
            return delegate.exchangeDeclare(exchange, type, durable, autoDelete, arguments);
        }

        @Override
        public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
            return delegate.exchangeDeclare(exchange, type, durable, autoDelete, internal, arguments);
        }

        @Override
        public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
            return delegate.exchangeDeclare(exchange, type, durable, autoDelete, internal, arguments);
        }

        @Override
        public void exchangeDeclareNoWait(String exchange, String type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
            delegate.exchangeDeclareNoWait(exchange, type, durable, autoDelete, internal, arguments);
        }

        @Override
        public void exchangeDeclareNoWait(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
            delegate.exchangeDeclare(exchange, type, durable, autoDelete, internal, arguments);
        }

        @Override
        public AMQP.Exchange.DeclareOk exchangeDeclarePassive(String name) throws IOException {
            return delegate.exchangeDeclarePassive(name);
        }

        @Override
        public AMQP.Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException {
            return delegate.exchangeDelete(exchange, ifUnused);
        }

        @Override
        public void exchangeDeleteNoWait(String exchange, boolean ifUnused) throws IOException {
            delegate.exchangeDeleteNoWait(exchange, ifUnused);
        }

        @Override
        public AMQP.Exchange.DeleteOk exchangeDelete(String exchange) throws IOException {
            return  delegate.exchangeDelete(exchange);
        }

        @Override
        public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey) throws IOException {
            return delegate.exchangeBind(destination, source, routingKey);
        }

        @Override
        public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
            return delegate.exchangeBind(destination, source, routingKey, arguments);
        }

        @Override
        public void exchangeBindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
            delegate.exchangeBindNoWait(destination, source, routingKey, arguments);
        }

        @Override
        public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey) throws IOException {
            return delegate.exchangeUnbind(destination, source, routingKey);
        }

        @Override
        public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
            return delegate.exchangeUnbind(destination, source, routingKey, arguments);
        }

        @Override
        public void exchangeUnbindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
            delegate.exchangeBindNoWait(destination, source, routingKey, arguments);
        }

        @Override
        public AMQP.Queue.DeclareOk queueDeclare() throws IOException {
            return delegate.queueDeclare();
        }

        @Override
        public AMQP.Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException {
            return delegate.queueDeclare(queue, durable, exclusive, autoDelete, arguments);
        }

        @Override
        public void queueDeclareNoWait(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException {
            delegate.queueDeclareNoWait(queue, durable, exclusive, autoDelete, arguments);
        }

        @Override
        public AMQP.Queue.DeclareOk queueDeclarePassive(String queue) throws IOException {
            return delegate.queueDeclarePassive(queue);
        }

        @Override
        public AMQP.Queue.DeleteOk queueDelete(String queue) throws IOException {
            return delegate.queueDelete(queue);
        }

        @Override
        public AMQP.Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
            return delegate.queueDelete(queue, ifUnused, ifEmpty);
        }

        @Override
        public void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
            delegate.queueDeleteNoWait(queue, ifUnused, ifEmpty);
        }

        @Override
        public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException {
            return delegate.queueBind(queue, exchange, routingKey);
        }

        @Override
        public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
            return delegate.queueBind(queue, exchange, routingKey, arguments);
        }

        @Override
        public void queueBindNoWait(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
            delegate.queueBindNoWait(queue, exchange, routingKey, arguments);
        }

        @Override
        public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey) throws IOException {
            return delegate.queueUnbind(queue, exchange, routingKey);
        }

        @Override
        public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
            return delegate.queueUnbind(queue, exchange, routingKey, arguments);
        }

        @Override
        public AMQP.Queue.PurgeOk queuePurge(String queue) throws IOException {
            return delegate.queuePurge(queue);
        }

        @Override
        public GetResponse basicGet(String queue, boolean autoAck) throws IOException {
            return delegate.basicGet(queue, autoAck);
        }

        @Override
        public void basicAck(long deliveryTag, boolean multiple) throws IOException {
            delegate.basicAck(deliveryTag, multiple);
        }

        @Override
        public void basicNack(long deliveryTag, boolean multiple, boolean requeue) throws IOException {
            delegate.basicNack(deliveryTag, multiple, requeue);
        }

        @Override
        public void basicReject(long deliveryTag, boolean requeue) throws IOException {
            delegate.basicReject(deliveryTag, requeue);
        }

        @Override
        public String basicConsume(String queue, Consumer callback) throws IOException {
            return delegate.basicConsume(queue, callback);
        }

        @Override
        public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
            return delegate.basicConsume(queue, deliverCallback, cancelCallback);
        }

        @Override
        public String basicConsume(String queue, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, deliverCallback, shutdownSignalCallback);
        }

        @Override
        public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, deliverCallback, cancelCallback, shutdownSignalCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Consumer callback) throws IOException {
            return delegate.basicConsume(queue, autoAck, callback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, deliverCallback, cancelCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, deliverCallback, shutdownSignalCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, deliverCallback, cancelCallback, shutdownSignalCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, Consumer callback) throws IOException {
            return delegate.basicConsume(queue, autoAck, arguments, callback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, arguments, deliverCallback, cancelCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, arguments, deliverCallback, shutdownSignalCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, arguments, deliverCallback, cancelCallback, shutdownSignalCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, Consumer callback) throws IOException {
            return delegate.basicConsume(queue, autoAck, consumerTag, callback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, consumerTag, deliverCallback, cancelCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, consumerTag, deliverCallback, shutdownSignalCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, consumerTag, deliverCallback, cancelCallback, shutdownSignalCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, Consumer callback) throws IOException {
            return delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, callback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, deliverCallback, cancelCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, deliverCallback, shutdownSignalCallback);
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
            return delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, deliverCallback, cancelCallback, shutdownSignalCallback);
        }

        @Override
        public void basicCancel(String consumerTag) throws IOException {
            delegate.basicCancel(consumerTag);
        }

        @Override
        public AMQP.Basic.RecoverOk basicRecover() throws IOException {
            return delegate.basicRecover();
        }

        @Override
        public AMQP.Basic.RecoverOk basicRecover(boolean requeue) throws IOException {
            return delegate.basicRecover(requeue);
        }

        @Override
        public AMQP.Tx.SelectOk txSelect() throws IOException {
            return delegate.txSelect();
        }

        @Override
        public AMQP.Tx.CommitOk txCommit() throws IOException {
            return delegate.txCommit();
        }

        @Override
        public AMQP.Tx.RollbackOk txRollback() throws IOException {
            return delegate.txRollback();
        }

        @Override
        public long getNextPublishSeqNo() {
            return delegate.getNextPublishSeqNo();
        }

        @Override
        public boolean waitForConfirms() throws InterruptedException {
            return delegate.waitForConfirms();
        }

        @Override
        public boolean waitForConfirms(long timeout) throws InterruptedException, TimeoutException {
            return delegate.waitForConfirms(timeout);
        }

        @Override
        public void waitForConfirmsOrDie() throws IOException, InterruptedException {
            delegate.waitForConfirmsOrDie();
        }

        @Override
        public void waitForConfirmsOrDie(long timeout) throws IOException, InterruptedException, TimeoutException {
            delegate.waitForConfirms(timeout);
        }

        @Override
        public void asyncRpc(Method method) throws IOException {
            delegate.asyncRpc(method);
        }

        @Override
        public Command rpc(Method method) throws IOException {
            return delegate.rpc(method);
        }

        @Override
        public long messageCount(String queue) throws IOException {
            return delegate.messageCount(queue);
        }

        @Override
        public long consumerCount(String queue) throws IOException {
            return delegate.consumerCount(queue);
        }

        @Override
        public CompletableFuture<Command> asyncCompletableRpc(Method method) throws IOException {
            return delegate.asyncCompletableRpc(method);
        }

        @Override
        public void addShutdownListener(ShutdownListener listener) {
            delegate.addShutdownListener(listener);
        }

        @Override
        public void removeShutdownListener(ShutdownListener listener) {
            delegate.removeShutdownListener(listener);
        }

        @Override
        public ShutdownSignalException getCloseReason() {
            return delegate.getCloseReason();
        }

        @Override
        public void notifyListeners() {
            delegate.notifyListeners();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }
    }

    public static class Configuration {
        @FunctionalInterface
        public interface RequiresRetries {
            RequiredMaxBorrowDelay retries(int retries);
        }

        @FunctionalInterface
        public interface RequiredMaxBorrowDelay {
            RequiredMaxChannel maxBorrowDelay(Duration maxBorrowDelay);
        }

        @FunctionalInterface
        public interface RequiredMaxChannel {
            Configuration maxChannel(int maxChannel);
        }

        public static final Configuration DEFAULT = builder()
            .retries(MAX_BORROW_RETRIES)
            .maxBorrowDelay(MAX_BORROW_DELAY)
            .maxChannel(MAX_CHANNELS_NUMBER);

        public static RequiresRetries builder() {
            return retries -> maxBorrowDelay -> maxChannel -> new Configuration(maxBorrowDelay, retries, maxChannel);
        }

        public static Configuration from(org.apache.commons.configuration2.Configuration configuration) {
            Duration maxBorrowDelay = Optional.ofNullable(configuration.getLong("channel.pool.max.delay.ms", null))
                    .map(Duration::ofMillis)
                    .orElse(MAX_BORROW_DELAY);

            return builder()
                .retries(configuration.getInt("channel.pool.retries", MAX_BORROW_RETRIES))
                .maxBorrowDelay(maxBorrowDelay)
                .maxChannel(configuration.getInt("channel.pool.size", MAX_CHANNELS_NUMBER));
        }

        private final Duration maxBorrowDelay;
        private final int retries;
        private final int maxChannel;

        public Configuration(Duration maxBorrowDelay, int retries, int maxChannel) {
            this.maxBorrowDelay = maxBorrowDelay;
            this.retries = retries;
            this.maxChannel = maxChannel;
        }

        private RetryBackoffSpec backoffSpec() {
            return Retry.backoff(retries, maxBorrowDelay);
        }

        public int getMaxChannel() {
            return maxChannel;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactorRabbitMQChannelPool.class);
    private static final int MAX_CHANNELS_NUMBER = 3;
    private static final int MAX_BORROW_RETRIES = 3;
    private static final Duration MAX_BORROW_DELAY = Duration.ofSeconds(5);

    private final ConcurrentHashMap<Integer, PooledRef<? extends Channel>> refs = new ConcurrentHashMap<>();
    private final Mono<Connection> connectionMono;
    private final Configuration configuration;

    private final InstrumentedPool<? extends Channel> newPool;
    private final MetricFactory metricFactory;
    private Sender sender;

    public ReactorRabbitMQChannelPool(Mono<Connection> connectionMono, Configuration configuration, MetricFactory metricFactory) {
        this.connectionMono = connectionMono;
        this.configuration = configuration;
        this.metricFactory = metricFactory;

        newPool = PoolBuilder.from(connectionMono
            .flatMap(this::openChannel))
            .sizeBetween(1, configuration.maxChannel)
            .maxPendingAcquireUnbounded()
            .evictionPredicate((channel, metadata) -> {
                if (!channel.isOpen()) {
                    return true;
                }
                if (metadata.idleTime() > Duration.ofSeconds(30).toMillis()) {
                    return true;
                }
                return false;
            })
            .destroyHandler(channel -> Mono.fromRunnable(Throwing.runnable(() -> {
                if (channel.isOpen()) {
                    channel.close();
                }
            }))
            .then()
            .subscribeOn(Schedulers.boundedElastic()))
            .buildPool();
    }

    private Mono<? extends Channel> openChannel(Connection connection) {
        return Mono.fromCallable(connection::openChannel)
            .map(maybeChannel ->
                maybeChannel.orElseThrow(() -> new RuntimeException("RabbitMQ reached to maximum opened channels, cannot get more channels")))
            .map(SelectOnceChannel::new)
            .retryWhen(configuration.backoffSpec().scheduler(Schedulers.boundedElastic()))
            .doOnError(throwable -> LOGGER.error("error when creating new channel", throwable));
    }

    public void start() {
        sender = createSender();
    }

    public Sender getSender() {
        return sender;
    }

    public Receiver createReceiver() {
        return RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionMono));
    }

    @Override
    public Mono<? extends Channel> getChannelMono() {
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("rabbit-acquire", borrow()));
    }

    private Mono<? extends Channel> borrow() {
        return newPool.acquire()
            .timeout(configuration.maxBorrowDelay)
            .doOnNext(ref -> refs.put(ref.poolable().getChannelNumber(), ref))
            .map(PooledRef::poolable)
            .onErrorMap(TimeoutException.class, e -> new NoSuchElementException("Timeout waiting for idle object"));
    }

    @Override
    public BiConsumer<SignalType, Channel> getChannelCloseHandler() {
        return (signalType, channel) -> metricFactory.runPublishingTimerMetric("rabbit-release",
            () -> {
                PooledRef<? extends Channel> pooledRef = refs.remove(channel.getChannelNumber());

                if (!channel.isOpen() || !executeWithoutError(signalType)) {
                    pooledRef.invalidate()
                        .subscribe();
                    return;
                }
                pooledRef.release()
                    .subscribe();
            });
    }

    private boolean executeWithoutError(SignalType signalType) {
        return signalType == SignalType.ON_COMPLETE
            || signalType == SignalType.CANCEL;
    }

    private Sender createSender() {
       return RabbitFlux.createSender(new SenderOptions()
           .connectionMono(connectionMono)
           .channelPool(this)
           .resourceManagementChannelMono(
               connectionMono.map(Throwing.function(Connection::createChannel)).cache()));
    }

    public Mono<Void> createWorkQueue(QueueSpecification queueSpecification, BindingSpecification bindingSpecification) {
        Preconditions.checkArgument(queueSpecification.getName() != null, "WorkQueue pattern do not make sense for unnamed queues");
        Preconditions.checkArgument(queueSpecification.getName().equals(bindingSpecification.getQueue()),
            "Binding needs to be targetting the created queue %s instead of %s",
            queueSpecification.getName(), bindingSpecification.getQueue());

        return Flux.concat(
            Mono.using(this::createSender,
                managementSender -> managementSender.declareQueue(queueSpecification),
                Sender::close)
                .onErrorResume(
                    e -> e instanceof ShutdownSignalException
                        && e.getMessage().contains("reply-code=406, reply-text=PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange' for queue"),
                    e -> {
                        LOGGER.warn("{} already exists without dead-letter setup. Dead lettered messages to it will be lost. " +
                            "To solve this, re-create the queue with the x-dead-letter-exchange argument set up.",
                            queueSpecification.getName());
                        return Mono.empty();
                    }),
            sender.bind(bindingSpecification))
            .then();
    }

    public Mono<Void> createWorkQueue(QueueSpecification queueSpecification) {
        Preconditions.checkArgument(queueSpecification.getName() != null, "WorkQueue pattern do not make sense for unnamed queues");

        return Mono.using(this::createSender,
            managementSender -> managementSender.declareQueue(queueSpecification),
            Sender::close)
            .onErrorResume(
                e -> e instanceof ShutdownSignalException
                    && e.getMessage().contains("reply-code=406, reply-text=PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange' for queue"),
                e -> {
                    LOGGER.warn("{} already exists without dead-letter setup. Dead lettered messages to it will be lost. " +
                            "To solve this, re-create the queue with the x-dead-letter-exchange argument set up.",
                        queueSpecification.getName());
                    return Mono.empty();
                })
            .then();
    }

    @PreDestroy
    @Override
    public void close() {
        sender.close();
       Flux.fromIterable(refs.values())
           .flatMap(PooledRef::invalidate)
           .blockLast();
        refs.clear();
        newPool.dispose();
    }

    public Mono<Boolean> tryChannel() {
        return Mono.usingWhen(borrow(),
            channel -> Mono.just(channel.isOpen()),
            channel -> {
                if (channel != null) {
                    PooledRef<? extends Channel> pooledRef = refs.remove(channel.getChannelNumber());
                    return pooledRef.release();
                }
                return Mono.empty();
            })
            .onErrorResume(any -> Mono.just(false));
    }
}

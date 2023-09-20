/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.task.eventsourcing.distributed;

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static org.apache.james.backends.rabbitmq.Constants.ALLOW_QUORUM;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.REQUEUE;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManagerWorker;
import org.apache.james.task.TaskWithId;
import org.apache.james.task.WorkQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;
import reactor.util.retry.Retry;

public class RabbitMQWorkQueue implements WorkQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQWorkQueue.class);

    static final String EXCHANGE_NAME = "taskManagerWorkQueueExchange";
    static final String QUEUE_NAME = "taskManagerWorkQueue";
    static final String ROUTING_KEY = "taskManagerWorkQueueRoutingKey";

    static final String CANCEL_REQUESTS_EXCHANGE_NAME = "taskManagerCancelRequestsExchange";
    static final String CANCEL_REQUESTS_ROUTING_KEY = "taskManagerCancelRequestsRoutingKey";
    public static final String TASK_ID = "taskId";

    public static final int NUM_RETRIES = 8;
    public static final Duration FIRST_BACKOFF = Duration.ofMillis(100);

    private final TaskManagerWorker worker;
    private final JsonTaskSerializer taskSerializer;
    private final RabbitMQWorkQueueConfiguration configuration;
    private final Sender sender;
    private final ReceiverProvider receiverProvider;
    private final RabbitMQConfiguration rabbitMQConfiguration;
    private final CancelRequestQueueName cancelRequestQueueName;
    private UnicastProcessor<TaskId> sendCancelRequestsQueue;
    private Disposable sendCancelRequestsQueueHandle;
    private Disposable receiverHandle;
    private Disposable cancelRequestListenerHandle;

    public RabbitMQWorkQueue(TaskManagerWorker worker, Sender sender,
                             ReceiverProvider receiverProvider, JsonTaskSerializer taskSerializer,
                             RabbitMQWorkQueueConfiguration configuration, CancelRequestQueueName cancelRequestQueueName,
                             RabbitMQConfiguration rabbitMQConfiguration) {
        this.cancelRequestQueueName = cancelRequestQueueName;
        this.worker = worker;
        this.receiverProvider = receiverProvider;
        this.sender = sender;
        this.taskSerializer = taskSerializer;
        this.configuration = configuration;
        this.rabbitMQConfiguration = rabbitMQConfiguration;
    }

    @Override
    public void start() {
        startWorkqueue();
        listenToCancelRequests();
    }

    private void startWorkqueue() {
        declareQueue();

        if (configuration.enabled()) {
            consumeWorkqueue();
        }
    }

    @VisibleForTesting
    void declareQueue() {
        Mono<AMQP.Exchange.DeclareOk> declareExchange = sender
            .declareExchange(ExchangeSpecification.exchange(EXCHANGE_NAME))
            .retryWhen(Retry.backoff(NUM_RETRIES, FIRST_BACKOFF));
        Mono<AMQP.Queue.DeclareOk> declareQueue = sender
            .declare(QueueSpecification.queue(QUEUE_NAME)
                .durable(true)
                .arguments(rabbitMQConfiguration.workQueueArgumentsBuilder(ALLOW_QUORUM)
                    .singleActiveConsumer()
                    .build()))
            .retryWhen(Retry.backoff(NUM_RETRIES, FIRST_BACKOFF));
        Mono<AMQP.Queue.BindOk> bindQueueToExchange = sender
            .bind(BindingSpecification.binding(EXCHANGE_NAME, ROUTING_KEY, QUEUE_NAME))
            .retryWhen(Retry.backoff(NUM_RETRIES, FIRST_BACKOFF));

        declareExchange
            .then(declareQueue)
            .then(bindQueueToExchange)
            .block();
    }

    @Override
    public void restart() {
        Disposable previousWorkQueueHandler = receiverHandle;
        consumeWorkqueue();
        previousWorkQueueHandler.dispose();

        Disposable previousCancelHandler = this.cancelRequestListenerHandle;
        registerCancelRequestsListener(cancelRequestQueueName.asString());
        previousCancelHandler.dispose();
    }

    private void consumeWorkqueue() {
        receiverHandle = Flux.using(
                receiverProvider::createReceiver,
                receiver -> receiver.consumeManualAck(QUEUE_NAME, new ConsumeOptions()),
                Receiver::close)
            .subscribeOn(Schedulers.elastic())
            .concatMap(this::executeTask)
            .subscribe();
    }

    private Mono<Task.Result> executeTask(AcknowledgableDelivery delivery) {
        return Mono.fromCallable(() -> delivery.getProperties().getHeaders())
            .map(headers -> headers.get(TASK_ID))
            .map(taskIdValue -> TaskId.fromString(taskIdValue.toString()))
            .flatMap(taskId -> Mono.fromCallable(() -> new String(delivery.getBody(), StandardCharsets.UTF_8))
                .flatMap(bodyValue -> deserialize(bodyValue, taskId))
                .doOnNext(task -> delivery.ack())
                .flatMap(task -> executeOnWorker(taskId, task)))
            .onErrorResume(error -> {
                Optional<Object> taskId = Optional.ofNullable(delivery.getProperties())
                    .flatMap(props -> Optional.ofNullable(props.getHeaders()))
                    .flatMap(headers -> Optional.ofNullable(headers.get(TASK_ID)));
                LOGGER.error("Unable to process {} {}", TASK_ID, taskId, error);
                delivery.nack(!REQUEUE);
                return Mono.empty();
            });
    }

    private Mono<Task> deserialize(String json, TaskId taskId) {
        return Mono.fromCallable(() -> taskSerializer.deserialize(json))
            .onErrorResume(error -> {
                String errorMessage = String.format("Unable to deserialize submitted Task %s", taskId.asString());
                LOGGER.error(errorMessage, error);
                return Mono.from(worker.fail(taskId, Optional.empty(), errorMessage, error))
                    .then(Mono.empty());
            });
    }

    private Mono<Task.Result> executeOnWorker(TaskId taskId, Task task) {
        return worker.executeTask(new TaskWithId(taskId, task))
            .onErrorResume(error -> {
                String errorMessage = String.format("Unable to run submitted Task %s", taskId.asString());
                LOGGER.warn(errorMessage, error);
                return Mono.from(worker.fail(taskId, task.details(), errorMessage, error))
                    .then(Mono.empty());
            });
    }

    private void listenToCancelRequests() {
        sender.declareExchange(ExchangeSpecification.exchange(CANCEL_REQUESTS_EXCHANGE_NAME)).block();
        sender.declare(QueueSpecification.queue(cancelRequestQueueName.asString()).durable(!DURABLE).autoDelete(AUTO_DELETE)).block();
        sender.bind(BindingSpecification.binding(CANCEL_REQUESTS_EXCHANGE_NAME, CANCEL_REQUESTS_ROUTING_KEY, cancelRequestQueueName.asString())).block();
        registerCancelRequestsListener(cancelRequestQueueName.asString());

        sendCancelRequestsQueue = UnicastProcessor.create();
        sendCancelRequestsQueueHandle = sender
            .send(sendCancelRequestsQueue.map(this::makeCancelRequestMessage))
            .subscribeOn(Schedulers.elastic())
            .subscribe();
    }

    private void registerCancelRequestsListener(String queueName) {
        cancelRequestListenerHandle = Flux.using(
                receiverProvider::createReceiver,
                receiver -> receiver.consumeAutoAck(queueName),
                Receiver::close)
            .subscribeOn(Schedulers.elastic())
            .map(this::readCancelRequestMessage)
            .doOnNext(worker::cancelTask)
            .subscribe();
    }

    private TaskId readCancelRequestMessage(Delivery delivery) {
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
        return TaskId.fromString(message);
    }

    private OutboundMessage makeCancelRequestMessage(TaskId taskId) {
        byte[] payload = taskId.asString().getBytes(StandardCharsets.UTF_8);
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder().build();
        return new OutboundMessage(CANCEL_REQUESTS_EXCHANGE_NAME, CANCEL_REQUESTS_ROUTING_KEY, basicProperties, payload);
    }

    @Override
    public void submit(TaskWithId taskWithId) {
        try {
            byte[] payload = taskSerializer.serialize(taskWithId.getTask()).getBytes(StandardCharsets.UTF_8);
            AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
                .priority(PERSISTENT_TEXT_PLAIN.getPriority())
                .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
                .headers(ImmutableMap.of(TASK_ID, taskWithId.getId().asString()))
                .build();

            OutboundMessage outboundMessage = new OutboundMessage(EXCHANGE_NAME, ROUTING_KEY, basicProperties, payload);
            sender.send(Mono.just(outboundMessage)).block();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cancel(TaskId taskId) {
        sendCancelRequestsQueue.onNext(taskId);
    }

    @Override
    public void close() {
        Optional.ofNullable(receiverHandle).ifPresent(Disposable::dispose);
        Optional.ofNullable(sendCancelRequestsQueueHandle).ifPresent(Disposable::dispose);
        Optional.ofNullable(cancelRequestListenerHandle).ifPresent(Disposable::dispose);
    }
}
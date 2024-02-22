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

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.lettuce.core.Consumer;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import reactor.core.publisher.Mono;

class RedisPlaygroundTest {

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    @Nested
    class StringsTest {
        @Test
        void shouldOverrideValueWhenTheSameKey(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String key = "KEY1";
            String keyValue = "Value1";
            client.set(key, keyValue);
            client.set(key, "overrideValue");

            assertThat(client.get(key)).isEqualTo("overrideValue");
        }

        @Test
        void getShouldReturnNullIfKeyDoesNotExist(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String notExistedKey = "notExistedKey";

            assertThat(client.get(notExistedKey)).isNull();
        }

        @Test
        void getDelShouldResetValue(DockerRedis redis) {
            RedisCommands<String, String> client = redis.createClient();
            String key = "KEY1";
            String keyValue = "Value1";
            client.set(key, keyValue);

            client.getdel(key);

            assertThat(client.get(key)).isNull();
        }
    }

    @Nested
    class RedisStreams {
        @Test
        void consumerGroupTest(DockerRedis redis) {
            RedisCommands<String, String> redisCommands = redis.createClient();

            String stream = "weather_sensor:wind";

            // create consumer group
            String consumerGroup = "jamesConsumers";
            redisCommands.xgroupCreate(XReadArgs.StreamOffset.from(stream, "0-0"), consumerGroup,
                XGroupCreateArgs.Builder.mkstream());

            // register 2 consumers to the stream
            publishMessageToRedisStream(redisCommands, stream);
            StreamMessage<String, String> messageByConsumer1 = redisCommands.xreadgroup(
                    Consumer.from(consumerGroup, "consumer_1"),
                    XReadArgs.StreamOffset.lastConsumed(stream))
                .get(0);
            System.out.println("Consumer is consuming message with id " + messageByConsumer1.getId());

            publishMessageToRedisStream(redisCommands, stream);
            StreamMessage<String, String> messageByConsumer2 = redisCommands.xreadgroup(
                    Consumer.from(consumerGroup, "consumer_2"),
                    XReadArgs.StreamOffset.lastConsumed(stream))
                .get(0);
            System.out.println("Consumer 2 is consuming message with id " + messageByConsumer2.getId());

            assertThat(messageByConsumer1.getId()).isNotEqualTo(messageByConsumer2.getId())
                .as("Consumer 2 does not consume the same message with Consumer 1");
        }

        @Test
        void ackedMessageTest(DockerRedis redis) {
            RedisCommands<String, String> redisCommands = redis.createClient();

            String stream = "weather_sensor:wind";

            // create consumer group
            String consumerGroup = "jamesConsumers";
            redisCommands.xgroupCreate(XReadArgs.StreamOffset.from(stream, "0-0"), consumerGroup,
                XGroupCreateArgs.Builder.mkstream());

            // GIVEN the consumer 1 does not acked the message after processing it e.g. because of failure
            publishMessageToRedisStream(redisCommands, stream);
            StreamMessage<String, String> messageByConsumer1 = redisCommands.xreadgroup(
                    Consumer.from(consumerGroup, "consumer_1"),
                    XReadArgs.StreamOffset.from(stream, ">"))
                .get(0);
            System.out.println("Consumer 1 failed to consume message with id " + messageByConsumer1.getId());

            // The consumer 1 can not see the unacked message using offset > (only return new and not unacked messages)
            assertThat(redisCommands.xreadgroup(Consumer.from(consumerGroup, "consumer_1"), XReadArgs.StreamOffset.from(stream, ">")))
                .isEmpty();

            // Other consumers can not see the unacked message even using offset 0 (because the unacked message is only visible to consumer 1 which tried to consume it)
            assertThat(redisCommands.xreadgroup(Consumer.from(consumerGroup, "consumer_2"), XReadArgs.StreamOffset.from(stream, "0")))
                .isEmpty();

            // THEN the consumer 1 can re-processing the unacked message using offset 0
            StreamMessage<String, String> messageReprocessingByConsumer1 = redisCommands.xreadgroup(
                    Consumer.from(consumerGroup, "consumer_1"),
                    XReadArgs.StreamOffset.from(stream, "0"))
                .get(0);
            assertThat(messageReprocessingByConsumer1.getId()).isEqualTo(messageByConsumer1.getId());
            // Confirm that the message has been processed using XACK
            redisCommands.xack(stream, consumerGroup, messageReprocessingByConsumer1.getId());
            System.out.println("Consumer 1 succeeded to re-consume message with id " + messageReprocessingByConsumer1.getId());

            // There should be no unacked message now
            assertThat(redisCommands.xpending(stream, consumerGroup)
                .getCount())
                .isZero();
        }

        @Test
        void nonConsumerGroupTest(DockerRedis redis) {
            // Goal: Test XREAD command (messages should be broadcast to all consumers)
            RedisCommands<String, String> redisCommands = redis.createClient();

            String stream = "weather_sensor:wind";

            // publish a message to a Redis Stream
            publishMessageToRedisStream(redisCommands, stream);

            // Consumer1 consumes the message 1st
            StreamMessage<String, String> messageByConsumer1 = redisCommands.xread(XReadArgs.StreamOffset.from(stream, "0"))
                .get(0);
            System.out.println("Consumer is consuming message with id " + messageByConsumer1.getId());

            // Assume consumer1 restarts and consume the message again from offset 0 (start point of all messages)
            StreamMessage<String, String> messageByConsumer1Again = redisCommands.xread(XReadArgs.StreamOffset.from(stream, "0"))
                .get(0);
            System.out.println("Consumer is consuming message with id " + messageByConsumer1Again.getId());

            assertThat(messageByConsumer1.getId()).isEqualTo(messageByConsumer1Again.getId())
                .as("Because XREAD does not support ACK message like XREADGROUP, consumer can keep consuming the same message upon restart. " +
                    "Therefore consumers needs to manage the (last consumed) message offset itself. Seems not convenient for the key registration case..." +
                    "Redis Pub/sub maybe more suitable for key registration case.");
        }
    }

    @Nested
    class RedisPubSub {

        class RedisPubSubListener extends RedisPubSubAdapter<String, String> {
            private final int consumerId;
            private final int listenerId;
            private final CountDownLatch latch;

            RedisPubSubListener(int consumerId, int listenerId, CountDownLatch latch) {
                this.consumerId = consumerId;
                this.listenerId = listenerId;
                this.latch = latch;
            }

            @Override
            public void message(String channel, String message) {
                System.out.println("Listener " + listenerId + " of Consumer " + consumerId + " received from channel " + channel + " message: " + message);
                latch.countDown();
            }
        }

        @Test
        void pubSubShouldPublishMessageToAllConsumers(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(2);

            // create 2 consumers to the Redis Pub/Sub channel, each consumer has 2 listeners
            StatefulRedisPubSubConnection<String, String> consumer1Connection = redis.createRawRedisClient().connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.sync().subscribe(channel);

            StatefulRedisPubSubConnection<String, String> consumer2Connection = redis.createRawRedisClient().connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.sync().subscribe(channel);

            // Publish a message to the channel
            RedisPubSubCommands<String, String> publisher = redis.createRawRedisClient().connectPubSub().sync();
            publisher.publish(channel, message);

            // Wait for the latch to count down, indicating that the message has been received by 2 consumer
            latch.await();

            // Assert that the message was received
            assertEquals(0, latch.getCount(), "Message was not fully received");
        }

        @Test
        void allListenersShouldBeNotifiedAboutTheMessage(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(4);

            // create 2 consumers to the Redis Pub/Sub channel, each consumer have 2 attached listeners (to handle the message)
            StatefulRedisPubSubConnection<String, String> consumer1Connection = redis.createRawRedisClient().connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.addListener(new RedisPubSubListener(1, 2, latch));
            consumer1Connection.sync().subscribe(channel);

            StatefulRedisPubSubConnection<String, String> consumer2Connection = redis.createRawRedisClient().connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.addListener(new RedisPubSubListener(2, 2, latch));
            consumer2Connection.sync().subscribe(channel);

            // Publish a message to the channel
            RedisPubSubCommands<String, String> publisher = redis.createRawRedisClient().connectPubSub().sync();
            publisher.publish(channel, message);

            // Wait for the latch to count down, indicating that the message has been received by 2 consumer
            latch.await();

            // Assert that the message was received
            assertEquals(0, latch.getCount(), "Message was not fully received");
        }

        @Test
        void pubSubShouldNotPublishMessageUnsubscribedConsumer(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(2);

            // create 2 consumers to the Redis Pub/Sub channel, each consumer has 2 listeners
            StatefulRedisPubSubConnection<String, String> consumer1Connection = redis.createRawRedisClient().connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.sync().subscribe(channel);

            StatefulRedisPubSubConnection<String, String> consumer2Connection = redis.createRawRedisClient().connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.sync().subscribe(channel);

            // consumer 2 unsubscribes the channel
            consumer2Connection.sync().unsubscribe(channel);

            // Publish a message to the channel
            RedisPubSubCommands<String, String> publisher = redis.createRawRedisClient().connectPubSub().sync();
            publisher.publish(channel, message);

            // Wait for the latch to count down
            latch.await(3, TimeUnit.SECONDS);

            // Assert that the message was received only by consumer 1
            assertThat(latch.getCount()).isEqualTo(1);
        }

        @Test
        void laterSubscribeWouldNotSeeThePublishedMessage(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(2);

            // Consumer 1 subscribes to the Redis Pub/Sub channel
            StatefulRedisPubSubConnection<String, String> consumer1Connection = redis.createRawRedisClient().connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.sync().subscribe(channel);

            // Publish a message to the channel
            RedisPubSubCommands<String, String> publisher = redis.createRawRedisClient().connectPubSub().sync();
            publisher.publish(channel, message);

            // Later... consumer 2 subscribes to the Redis Pub/Sub channel
            StatefulRedisPubSubConnection<String, String> consumer2Connection = redis.createRawRedisClient().connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.sync().subscribe(channel);

            // Wait for the latch to count down
            latch.await(3, TimeUnit.SECONDS);

            // The message has been received by only consumer 1. Consumer 2 does not connect / subscribe to the channel when the message is being fired and forget, therefore it does not receive the message.
            assertThat(latch.getCount()).isEqualTo(1);
        }

        @Test
        void laterSubscribeWouldNotSeeThePublishedMessageReactive(DockerRedis redis) throws InterruptedException {
            String channel = "testChannel";
            String message = "Hello, Lettuce Redis Pub/Sub!";

            // Set up a latch to wait for the message
            CountDownLatch latch = new CountDownLatch(2);

            // Consumer 1 subscribes to the Redis Pub/Sub channel
            StatefulRedisPubSubConnection<String, String> consumer1Connection = redis.createRawRedisClient().connectPubSub();
            consumer1Connection.addListener(new RedisPubSubListener(1, 1, latch));
            consumer1Connection.reactive().subscribe(channel).block();

            // Publish a message to the channel
            RedisPubSubReactiveCommands<String, String> publisher = redis.createRawRedisClient().connectPubSub().reactive();
            Mono<Long> receivedConsumersCountMono = publisher.publish(channel, message);
            Long receivedConsumersCount = receivedConsumersCountMono.block();

            // Later... consumer 2 subscribes to the Redis Pub/Sub channel
            StatefulRedisPubSubConnection<String, String> consumer2Connection = redis.createRawRedisClient().connectPubSub();
            consumer2Connection.addListener(new RedisPubSubListener(2, 1, latch));
            consumer2Connection.reactive().subscribe(channel).block();

            // Wait for the latch to count down
            latch.await(3, TimeUnit.SECONDS);

            // The message has been received by only consumer 1. Consumer 2 does not connect / subscribe to the channel when the message is being fired and forget, therefore it does not receive the message.
            assertThat(receivedConsumersCount).isEqualTo(1L);
        }
    }

    private void publishMessageToRedisStream(RedisCommands<String, String> redisCommands, String redisStream) {
        Map<String, String> messageBody = new HashMap<>();
        messageBody.put( "speed", "15" );
        messageBody.put( "direction", "270" );
        messageBody.put( "sensor_ts", String.valueOf(System.currentTimeMillis()));
        String messageId = redisCommands.xadd(redisStream, messageBody);
        System.out.println(String.format("Message with id %s : %s published to Redis Streams", messageId, messageBody));
    }

}

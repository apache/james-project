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

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.MoreObjects;
import feign.Feign;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.RetryableException;
import feign.Retryer;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public interface RabbitMQManagementAPI {

    class MessageQueue {
        @JsonProperty("name")
        String name;

        @JsonProperty("vhost")
        String vhost;

        @JsonProperty("auto_delete")
        boolean autoDelete;

        @JsonProperty("durable")
        boolean durable;

        @JsonProperty("exclusive")
        boolean exclusive;

        @JsonProperty("arguments")
        Map<String, String> arguments;

        public String getName() {
            return name;
        }

        public String getVhost() {
            return vhost;
        }

        public boolean isAutoDelete() {
            return autoDelete;
        }

        public boolean isDurable() {
            return durable;
        }

        public boolean isExclusive() {
            return exclusive;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }
    }

    class MessageQueueDetails {
        @JsonProperty("name")
        String name;

        @JsonProperty("vhost")
        String vhost;

        @JsonProperty("auto_delete")
        boolean autoDelete;

        @JsonProperty("durable")
        boolean durable;

        @JsonProperty("exclusive")
        boolean exclusive;

        @JsonProperty("arguments")
        Map<String, String> arguments;

        @JsonProperty("consumer_details")
        List<ConsumerDetails> consumerDetails;

        public String getName() {
            return name;
        }

        public String getVhost() {
            return vhost;
        }

        public boolean isAutoDelete() {
            return autoDelete;
        }

        public boolean isDurable() {
            return durable;
        }

        public boolean isExclusive() {
            return exclusive;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }

        public List<ConsumerDetails> getConsumerDetails() {
            return consumerDetails;
        }
    }

    class ConsumerDetails {
        @JsonProperty("consumer_tag")
        String tag;

        @JsonProperty("activity_status")
        ActivityStatus status;

        public ActivityStatus getStatus() {
            return this.status;
        }

        public String getTag() {
            return this.tag;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    enum ActivityStatus {
        Waiting("waiting"),
        SingleActive("single_active");

        private final String value;

        ActivityStatus(String value) {
            this.value = value;
        }

        @JsonValue
        String getValue() {
            return value;
        }
    }

    class Exchange {

        @JsonProperty("name")
        String name;

        @JsonProperty("type")
        String type;

        @JsonProperty("auto_delete")
        boolean autoDelete;

        @JsonProperty("durable")
        boolean durable;

        @JsonProperty("internal")
        boolean internal;

        @JsonProperty("arguments")
        Map<String, String> arguments;

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isAutoDelete() {
            return autoDelete;
        }

        public boolean isDurable() {
            return durable;
        }

        public boolean isInternal() {
            return internal;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("type", type)
                .add("autoDelete", autoDelete)
                .add("durable", durable)
                .add("internal", internal)
                .add("arguments", arguments)
                .toString();
        }
    }

    static RabbitMQManagementAPI from(RabbitMQConfiguration configuration) {
        RabbitMQConfiguration.ManagementCredentials credentials = configuration.getManagementCredentials();
        return Feign.builder()
            .requestInterceptor(new BasicAuthRequestInterceptor(credentials.getUser(), new String(credentials.getPassword())))
            .logger(new Slf4jLogger(RabbitMQManagementAPI.class))
            .logLevel(Logger.Level.FULL)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .retryer(new Retryer.Default())
            .errorDecoder(RETRY_500)
            .target(RabbitMQManagementAPI.class, configuration.getManagementUri().toString());
    }

    ErrorDecoder RETRY_500 = (methodKey, response) -> {
        if (response.status() == 500) {
            throw new RetryableException(response.status(), "Error encountered, scheduling retry", response.request().httpMethod(), new Date());
        }
        throw new RuntimeException("Non recoverable exception status: " + response.status());
    };

    @RequestLine("GET /api/queues")
    List<MessageQueue> listQueues();

    @RequestLine(value = "GET /api/queues/{vhost}/{name}", decodeSlash = false)
    MessageQueueDetails queueDetails(@Param("vhost") String vhost, @Param("name") String name);

    @RequestLine(value = "DELETE /api/queues/{vhost}/{name}", decodeSlash = false)
    void deleteQueue(@Param("vhost") String vhost, @Param("name") String name);

    @RequestLine("GET /api/exchanges")
    List<Exchange> listExchanges();
}
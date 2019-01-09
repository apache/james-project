
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

package org.apache.james.backend.rabbitmq;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

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

        public String getName() {
            return name;
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
            throw new RetryableException("Error encountered, scheduling retry", response.request().httpMethod(), new Date());
        }
        throw new RuntimeException("Non recoverable exception status: " + response.status());
    };

    @RequestLine("GET /api/queues")
    List<MessageQueue> listQueues();

    @RequestLine(value = "DELETE /api/queues/{vhost}/{name}", decodeSlash = false)
    void deleteQueue(@Param("vhost") String vhost, @Param("name") String name);
}
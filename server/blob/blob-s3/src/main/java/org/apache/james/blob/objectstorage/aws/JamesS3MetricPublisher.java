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

package org.apache.james.blob.objectstorage.aws;

import static software.amazon.awssdk.core.metrics.CoreMetric.API_CALL_DURATION;
import static software.amazon.awssdk.http.HttpMetric.AVAILABLE_CONCURRENCY;
import static software.amazon.awssdk.http.HttpMetric.CONCURRENCY_ACQUIRE_DURATION;
import static software.amazon.awssdk.http.HttpMetric.LEASED_CONCURRENCY;
import static software.amazon.awssdk.http.HttpMetric.PENDING_CONCURRENCY_ACQUIRES;

import java.time.Duration;

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;

import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;

public class JamesS3MetricPublisher implements MetricPublisher {
    private final GaugeRegistry.SettableGauge<Integer> availableConcurrency; // The number of remaining concurrent requests that can be supported by the HTTP client without needing to establish another connection.
    private final GaugeRegistry.SettableGauge<Integer> leasedConcurrency; // The number of request currently being executed by the HTTP client.
    private final GaugeRegistry.SettableGauge<Integer> pendingConcurrencyAcquires; // The number of requests that are blocked, waiting for another TCP connection or a new stream to be available from the connection pool.
    private final TimeMetric concurrencyAcquireDuration; // The time taken to acquire a channel from the connection pool.
    private final TimeMetric apiCallDuration; // The total time taken to finish a request (inclusive of all retries).

    public JamesS3MetricPublisher(MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        this.availableConcurrency = gaugeRegistry.settableGauge("s3_httpClient_availableConcurrency");
        this.leasedConcurrency = gaugeRegistry.settableGauge("s3_httpClient_leasedConcurrency");
        this.pendingConcurrencyAcquires = gaugeRegistry.settableGauge("s3_httpClient_pendingConcurrencyAcquires");
        this.concurrencyAcquireDuration = metricFactory.timer("s3_httpClient_concurrencyAcquireDuration");
        this.apiCallDuration = metricFactory.timer("s3_apiCall_apiCallDuration");
    }

    @Override
    public void publish(MetricCollection s3ClientMetrics) {
        extractS3ClientMetrics(s3ClientMetrics);
    }

    private void extractS3ClientMetrics(MetricCollection s3ClientMetrics) {
        // Extract useful metrics from https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/metrics-list.html
        s3ClientMetrics.stream().forEach(metricRecord -> {
            if (metricRecord.metric().equals(AVAILABLE_CONCURRENCY)) {
                availableConcurrency.setValue((Integer) metricRecord.value());
            }
            if (metricRecord.metric().equals(LEASED_CONCURRENCY)) {
                leasedConcurrency.setValue((Integer) metricRecord.value());
            }
            if (metricRecord.metric().equals(PENDING_CONCURRENCY_ACQUIRES)) {
                pendingConcurrencyAcquires.setValue((Integer) metricRecord.value());
            }
            if (metricRecord.metric().equals(CONCURRENCY_ACQUIRE_DURATION)) {
                concurrencyAcquireDuration.record((Duration) metricRecord.value());
            }
            if (metricRecord.metric().equals(API_CALL_DURATION)) {
                apiCallDuration.record((Duration) metricRecord.value());
            }
        });

        s3ClientMetrics.children().forEach(this::extractS3ClientMetrics);
    }

    @Override
    public void close() {
    }
}

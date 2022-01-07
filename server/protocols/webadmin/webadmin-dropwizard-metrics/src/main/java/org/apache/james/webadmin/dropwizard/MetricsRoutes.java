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

package org.apache.james.webadmin.dropwizard;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.webadmin.PublicRoutes;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.prometheus.client.SampleNameFilter;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.common.TextFormat;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * Content adapted from https://github.com/prometheus/client_java/blob/master/simpleclient_servlet/src/main/java/io/prometheus/client/exporter/MetricsServlet.java
 */
public class MetricsRoutes implements PublicRoutes {

    public static final String BASE = "/metrics";
    private final DropwizardExports dropwizardExports;

    @Inject
    public MetricsRoutes(MetricRegistry registry) {
        dropwizardExports = new DropwizardExports(registry);
    }

    @Override
    public String getBasePath() {
        return BASE;
    }

    @Override
    public void define(Service service) {
        service.get(BASE, this::getMetrics);
    }

    public Response getMetrics(Request request, Response response) throws IOException {
        Set<String> params = parse(request.raw());
        HttpServletResponse rawResponse = response.raw();
        rawResponse.setStatus(HttpServletResponse.SC_OK);
        rawResponse.setContentType(TextFormat.CONTENT_TYPE_004);

        try (Writer writer = new BufferedWriter(rawResponse.getWriter())) {
            SampleNameFilter nameFilter = new SampleNameFilter.Builder().nameMustBeEqualTo(params).build();
            TextFormat.write004(writer,
                Collections.enumeration(dropwizardExports.collect()
                .stream()
                .filter(e -> nameFilter.test(e.name))
                .collect(ImmutableList.toImmutableList())));
            writer.flush();
        }
        return response;
    }


    private Set<String> parse(HttpServletRequest req) {
        String[] includedParam = req.getParameterValues("name[]");

        return Optional.ofNullable(includedParam)
            .map(ImmutableSet::copyOf)
            .orElse(ImmutableSet.of());
    }
}

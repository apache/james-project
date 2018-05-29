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
package org.apache.james.jmap;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.jmap.methods.RequestHandler;
import org.apache.james.jmap.model.AuthenticatedProtocolRequest;
import org.apache.james.jmap.model.ProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JMAPServlet extends HttpServlet {

    public static final Logger LOGGER = LoggerFactory.getLogger(JMAPServlet.class);
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String JSON_CONTENT_TYPE_UTF8 = "application/json; charset=UTF-8";

    private final ObjectMapper objectMapper;
    private final RequestHandler requestHandler;
    private final MetricFactory metricFactory;

    @Inject
    public JMAPServlet(RequestHandler requestHandler, MetricFactory metricFactory) {
        this.requestHandler = requestHandler;
        this.metricFactory = metricFactory;
        this.objectMapper = new ObjectMapper();
        objectMapper.configure(Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        TimeMetric timeMetric = metricFactory.timer("JMAP-request");
        try {
            List<Object[]> responses =
                requestAsJsonStream(req)
                    .map(ProtocolRequest::deserialize)
                    .map(x -> AuthenticatedProtocolRequest.decorate(x, req))
                    .flatMap(this::handle)
                    .map(ProtocolResponse::asProtocolSpecification)
                    .collect(Collectors.toList());

            resp.setContentType(JSON_CONTENT_TYPE);
            objectMapper.writeValue(resp.getOutputStream(), responses);
        } catch (IOException e) {
            LOGGER.warn("error handling request", e);
            resp.setStatus(SC_BAD_REQUEST);
        } catch (Exception e) {
            LOGGER.error("Error handling request", e);
            throw new ServletException(e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private Stream<? extends ProtocolResponse> handle(AuthenticatedProtocolRequest request) {
        try {
            return requestHandler.handle(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<JsonNode[]> requestAsJsonStream(HttpServletRequest req) throws IOException, JsonParseException, JsonMappingException {
        return Arrays.stream(
                objectMapper.readValue(req.getInputStream(), JsonNode[][].class));
    }
}

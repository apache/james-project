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

package org.apache.james.protocols.webadmin;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.core.Username;
import org.apache.james.protocols.lib.netty.CertificateReloadable;
import org.apache.james.util.Port;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import spark.Request;
import spark.Service;

public class ProtocolServerRoutes implements Routes {
    public static final String SERVERS = "servers";
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {

    };

    private final Set<CertificateReloadable.Factory> servers;
    private final DisconnectorNotifier disconnector;

    @Inject
    public ProtocolServerRoutes(Set<CertificateReloadable.Factory> servers, DisconnectorNotifier disconnector) {
        this.servers = servers;
        this.disconnector = disconnector;
    }

    @Override
    public String getBasePath() {
        return SERVERS;
    }

    @Override
    public void define(Service service) {
        service.post(SERVERS, (request, response) -> {
            Preconditions.checkArgument(request.queryParams().contains("reload-certificate"),
                "'reload-certificate' query parameter shall be specified");

            if (noServerEnabled()) {
                return ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("No servers configured, nothing to reload")
                    .haltError();
            }

            servers.stream()
                .flatMap(CertificateReloadable.Factory::certificatesReloadable)
                .filter(filters(request))
                .forEach(Throwing.consumer(CertificateReloadable::reloadSSLCertificate));

            return Responses.returnNoContent(response);
        });

        service.delete(SERVERS + "/channels/:user", (request, response) -> {
            Username username = Username.of(request.params("user"));
            disconnector.disconnect(username::equals);

            return Responses.returnNoContent(response);
        });

        service.delete(SERVERS + "/channels", (request, response) -> {
            String body = request.body();

            if (Strings.isNullOrEmpty(body)) {
                disconnector.disconnect(any -> true);
            } else {
                ImmutableSet<Username> userSet = OBJECT_MAPPER.readValue(body, LIST_OF_STRING)
                    .stream()
                    .map(Username::of)
                    .collect(ImmutableSet.toImmutableSet());
                disconnector.disconnect(userSet::contains);
            }

            return Responses.returnNoContent(response);
        });
    }

    private Predicate<CertificateReloadable> filters(Request request) {
        Optional<Port> port = Optional.ofNullable(request.queryParams("port")).map(Integer::parseUnsignedInt).map(Port::of);

        return server -> port.map(p -> server.getPort() == p.getValue()).orElse(true);
    }

    private boolean noServerEnabled() {
        return servers.stream()
            .flatMap(CertificateReloadable.Factory::certificatesReloadable)
            .findFirst()
            .isEmpty();
    }
}

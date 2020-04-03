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

import org.reactivestreams.Publisher;

import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class JMAPRoute {
    public interface Action {
        Publisher<Void> handleRequest(HttpServerRequest request, HttpServerResponse response);
    }

    public static class Builder {
        @FunctionalInterface
        public interface RequireEndpoint {
            RequireAction endpoint(Endpoint endpoint);
        }

        @FunctionalInterface
        public interface RequireAction {
            ReadyToBuild action(Action action);
        }

        public static class ReadyToBuild {
            private final Endpoint endpoint;
            private final Action action;

            ReadyToBuild(Endpoint endpoint, Action action) {
                this.endpoint = endpoint;
                this.action = action;
            }

            public JMAPRoute corsHeaders() {
                return build(JMAPRoutes.corsHeaders(action));
            }

            public JMAPRoute noCorsHeaders() {
                return build(action);
            }

            private JMAPRoute build(Action action) {
                return new JMAPRoute(endpoint, actionWithParameterResolving(action));
            }

            Action actionWithParameterResolving(Action action) {
                return (req, res) ->
                    action.handleRequest(
                        req.paramsResolver(s -> endpoint.getUriPathTemplate().match(s)),
                        res);
            }
        }
    }

    public static Builder.RequireEndpoint builder() {
        return endpoint -> action -> new Builder.ReadyToBuild(endpoint, action);
    }

    private final Endpoint endpoint;
    private final Action action;

    private JMAPRoute(Endpoint endpoint, Action action) {
        this.endpoint = endpoint;
        this.action = action;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Action getAction() {
        return action;
    }

    public boolean matches(HttpServerRequest request) {
        return endpoint.matches(request);
    }
}

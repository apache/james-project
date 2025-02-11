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

package org.apache.james.webadmin.jettyserver;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee10.servlet.ServletContextRequest;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import spark.embeddedserver.jetty.HttpRequestWrapper;

/**
 * Simple Jetty Handler
 *
 * @author Per Wendel
 *
 * @see <a href="https://github.com/nmondal/spark-11/pull/20">Upstream issue</a>
 */
public class JettyHandler extends SessionHandler {
    private final Filter filter;

    public JettyHandler(Filter filter) {
        this.filter = filter;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        if (request instanceof ServletContextRequest servletContextRequest) {
            final HttpServletResponse httpServletResponse = servletContextRequest.getHttpServletResponse();
            final HttpServletRequest httpServletRequest = servletContextRequest.getServletApiRequest();
            final HttpRequestWrapper wrapper = new HttpRequestWrapper(httpServletRequest);

            filter.doFilter(wrapper, httpServletResponse, null);
            callback.succeeded();
            return true;
        }

        return false;
    }
}

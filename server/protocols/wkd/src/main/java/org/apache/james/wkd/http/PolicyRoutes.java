package org.apache.james.wkd.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.apache.james.wkd.http.WebKeyDirectoryUrls.WELLKNOWN_POLICY;

import org.apache.james.wkd.WebKeyDirectoryRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.http.server.HttpServerRoutes;

public class PolicyRoutes implements WebKeyDirectoryRoutes {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyRoutes.class);

    @Override
    public HttpServerRoutes define(HttpServerRoutes builder) {
        return builder.get(WELLKNOWN_POLICY, WebKeyDirectoryRoutes.corsHeaders(this::get)).options(WELLKNOWN_POLICY,
                CORS_CONTROL);
    }

    @VisibleForTesting
    Mono<Void> get(HttpServerRequest request, HttpServerResponse response) {
        return response.header(CONTENT_TYPE, "text/plain").status(OK).sendString(Mono.just("auth-submit")).then();
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

}

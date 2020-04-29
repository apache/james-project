package org.apache.james.wkd.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.apache.james.wkd.http.WebKeyDirectoryUrls.WELLKNOWN_SUBMISSION_ADDRESS;

import javax.inject.Inject;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.wkd.WebKeyDirectoryConfiguration;
import org.apache.james.wkd.WebKeyDirectoryRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.http.server.HttpServerRoutes;

public class SubmissionAddressRoutes implements WebKeyDirectoryRoutes {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionAddressRoutes.class);

    private DomainList domainList;

    @Inject
    public SubmissionAddressRoutes(DomainList domainList) {

        this.domainList = domainList;

    }

    @Override
    public HttpServerRoutes define(HttpServerRoutes builder) {
        return builder
            .get(WELLKNOWN_SUBMISSION_ADDRESS, WebKeyDirectoryRoutes.corsHeaders(this::get))
            .options(WELLKNOWN_SUBMISSION_ADDRESS, CORS_CONTROL);
    }

    @VisibleForTesting
    Mono<Void> get(HttpServerRequest request, HttpServerResponse response) {
        try {
            String defaultDomain = domainList.getDefaultDomain().asString();
            return response.header(CONTENT_TYPE, "text/plain").status(OK).sendString(Mono.just(
                WebKeyDirectoryConfiguration.SUBMISSION_ADDRESS_LOCAL_PART + "@" + defaultDomain))
                .then();
        } catch (DomainListException e) {
            LOGGER.error("Could not get default domain", e);
            return response.status(500).sendString(Mono.just(e.getMessage())).then();
        }
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

}

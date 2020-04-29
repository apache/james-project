package org.apache.james.wkd.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.apache.james.wkd.http.WebKeyDirectoryUrls.WELLKNOWN_DIRECT_PUB_KEY;

import javax.inject.Inject;

import org.apache.james.wkd.WebKeyDirectoryRoutes;
import org.apache.james.wkd.store.PublicKeyEntry;
import org.apache.james.wkd.store.WebKeyDirectoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.http.server.HttpServerRoutes;

public class DirectPubKeyRoutes implements WebKeyDirectoryRoutes {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectPubKeyRoutes.class);

    private static final String LOCAL_PART_HASH_PARAM = "localPartHash";

    private static final String WELLKNOWN_DIRECT_PUB_KEY_LOCAL_PART_HASH = String.format("%s/{%s}",
        WELLKNOWN_DIRECT_PUB_KEY, LOCAL_PART_HASH_PARAM);

    private WebKeyDirectoryStore webKeyDirectoryStore;

    @Inject
    @VisibleForTesting
    DirectPubKeyRoutes(WebKeyDirectoryStore webKeyDirectoryStore) {
        this.webKeyDirectoryStore = webKeyDirectoryStore;
    }

    @Override
    public HttpServerRoutes define(HttpServerRoutes builder) {
        return builder
            .get(WELLKNOWN_DIRECT_PUB_KEY_LOCAL_PART_HASH,
                WebKeyDirectoryRoutes.corsHeaders(this::get))
            .head(WELLKNOWN_DIRECT_PUB_KEY_LOCAL_PART_HASH,
                WebKeyDirectoryRoutes.corsHeaders(this::head))
            .options(WELLKNOWN_DIRECT_PUB_KEY, CORS_CONTROL);
    }

    @VisibleForTesting
    Mono<Void> get(HttpServerRequest request, HttpServerResponse response) {
        String localPartHash = request.param(LOCAL_PART_HASH_PARAM);

        PublicKeyEntry publicKeyEntry = webKeyDirectoryStore.get(localPartHash);

        if (publicKeyEntry == null) {
            return response.status(HttpResponseStatus.NOT_FOUND).then();
        } else {
            return response.header(CONTENT_TYPE, "application/octet-stream")
                .status(HttpResponseStatus.OK)
                .sendByteArray(Mono.just(publicKeyEntry.getPublicKey())).then();
        }
    }

    @VisibleForTesting
    Mono<Void> head(HttpServerRequest request, HttpServerResponse response) {
        String localPartHash = request.param(LOCAL_PART_HASH_PARAM);

        PublicKeyEntry publicKeyEntry = webKeyDirectoryStore.get(localPartHash);

        if (publicKeyEntry == null) {
            return response.status(HttpResponseStatus.NOT_FOUND).then();
        } else {
            return response.header(CONTENT_TYPE, "application/octet-stream")
                .status(HttpResponseStatus.OK).then();
        }
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

}

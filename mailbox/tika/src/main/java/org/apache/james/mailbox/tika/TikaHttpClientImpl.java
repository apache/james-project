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
package org.apache.james.mailbox.tika;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Optional;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TikaHttpClientImpl implements TikaHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TikaHttpClientImpl.class);
    private static final String RECURSIVE_METADATA_AS_TEXT_ENDPOINT = "/rmeta/text";

    private final TikaConfiguration tikaConfiguration;
    private final URI recursiveMetaData;

    public TikaHttpClientImpl(TikaConfiguration tikaConfiguration) throws URISyntaxException {
        this.tikaConfiguration = tikaConfiguration;
        this.recursiveMetaData = buildURI(tikaConfiguration).resolve(RECURSIVE_METADATA_AS_TEXT_ENDPOINT);
    }

    private URI buildURI(TikaConfiguration tikaConfiguration) throws URISyntaxException {
        return new URIBuilder()
                .setHost(tikaConfiguration.getHost())
                .setPort(tikaConfiguration.getPort())
                .setScheme("http")
                .build();
    }

    @Override
    public Optional<InputStream> recursiveMetaDataAsJson(InputStream inputStream, org.apache.james.mailbox.model.ContentType contentType) {
        try {
            ContentType httpContentType = ContentType.create(contentType.mimeType().asString(),
                contentType.charset()
                    .map(Charset::name)
                    .orElse(null));
            return Optional.ofNullable(
                    Request.Put(recursiveMetaData)
                        .socketTimeout(tikaConfiguration.getTimeoutInMillis())
                        .bodyStream(inputStream, httpContentType)
                        .execute()
                        .returnContent()
                        .asStream());
        } catch (IOException e) {
            LOGGER.warn("Failing to call Tika for content type {}", contentType, e);
            return Optional.empty();
        }
    }

}

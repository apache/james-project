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

package org.apache.james.webadmin.oidc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.oidc.OidcTokenCache;
import org.apache.james.oidc.Sid;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.PublicRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;

import spark.Route;
import spark.Service;

public class OidcBackchannelLogoutRoutes implements PublicRoutes {
    public static final String BASE_PATH = "/add-revoked-token";

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcBackchannelLogoutRoutes.class);
    private static final String APPLICATION_FORM_URLENCODED_VALUE = "application/x-www-form-urlencoded";
    private static final String TOKEN_PARAM = "logout_token";
    private static final String SID_PROPERTY = "sid";

    private final OidcTokenCache oidcTokenCache;
    private final ObjectMapper objectMapper;
    private final JsonTransformer jsonTransformer;

    @Inject
    public OidcBackchannelLogoutRoutes(OidcTokenCache oidcTokenCache, JsonTransformer jsonTransformer) {
        this.oidcTokenCache = oidcTokenCache;
        this.jsonTransformer = jsonTransformer;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(BASE_PATH, addRevokedToken(), jsonTransformer);
    }

    public Route addRevokedToken() {
        return (request, response) -> {
            if (!StringUtils.startsWith(request.contentType(), APPLICATION_FORM_URLENCODED_VALUE)) {
                response.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE_415);
                return "Unsupported Content-Type";
            }

            String token = request.queryParams(TOKEN_PARAM);
            Preconditions.checkArgument(StringUtils.isNotEmpty(token), "Missing logout token");

            Sid sid = extractSidFromLogoutToken(token);
            LOGGER.debug("Add new revoked token has sid: {}", sid);
            oidcTokenCache.invalidate(sid).block();
            return Constants.EMPTY_BODY;
        };
    }

    private Sid extractSidFromLogoutToken(String token) {
        try {
            List<String> parts = Splitter.on('.')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(token);
            if (parts.size() < 2) {
                throw new IllegalArgumentException("JWT does not contain the mandatory 2 parts");
            }

            String payloadJson = new String(BaseEncoding.base64Url().decode(parts.get(1)), StandardCharsets.UTF_8);
            Map<String, Object> payloadMap = objectMapper.readValue(payloadJson, new TypeReference<>() {
            });

            return Optional.ofNullable(payloadMap.getOrDefault(SID_PROPERTY, null))
                .map(s -> (String) s)
                .filter(sid -> !sid.isEmpty())
                .map(Sid::new)
                .orElseThrow(() -> new IllegalArgumentException("Unable to extract Sid from logout token"));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to extract Sid from logout token", exception);
        }
    }
}

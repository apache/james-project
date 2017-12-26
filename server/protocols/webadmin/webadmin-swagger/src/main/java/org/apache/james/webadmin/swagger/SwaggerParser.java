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

package org.apache.james.webadmin.swagger;

import javax.inject.Inject;

import org.apache.james.webadmin.WebAdminConfiguration;
import org.reflections.Reflections;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.Api;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Swagger;

@SwaggerDefinition
public class SwaggerParser {
    private static final String[] SCHEMES = new String[]{SwaggerDefinition.Scheme.HTTP.name(), SwaggerDefinition.Scheme.HTTPS.name()};
    private static final String JSON_TYPE = "application/json";
    private static final String API_DOC_VERSION = "V1.0";
    private static final String API_DOC_TITLE = "JAMES Web Admin API";
    private static final String API_DOC_DESCRIPTION = "All the web administration API for JAMES";
    public static final String HOST_PORT_SEPARATOR = ":";

    @Inject
    public static String getSwaggerJson(String packageName, WebAdminConfiguration configuration) throws JsonProcessingException {
        return swaggerToJson(getSwagger(packageName, configuration));
    }

    private static Swagger getSwagger(String packageName, WebAdminConfiguration configuration) {
        return new Reader(getSwagger(getBeanConfig(packageName, configuration)))
                .read(new Reflections(packageName)
                .getTypesAnnotatedWith(Api.class));
    }

    private static Swagger getSwagger(BeanConfig beanConfig) {
        Swagger swagger = beanConfig.getSwagger();

        swagger.addConsumes(JSON_TYPE);
        swagger.addProduces(JSON_TYPE);
        return swagger;
    }

    private static BeanConfig getBeanConfig(String packageName, WebAdminConfiguration configuration) {
        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setResourcePackage(packageName);
        beanConfig.setVersion(API_DOC_VERSION);
        beanConfig.setTitle(API_DOC_TITLE);
        beanConfig.setDescription(API_DOC_DESCRIPTION);
        beanConfig.setHost(configuration.getHost() + HOST_PORT_SEPARATOR + configuration.getPort().get().getValue());
        beanConfig.setSchemes(SCHEMES);
        beanConfig.setScan(true);
        beanConfig.scanAndRead();
        return beanConfig;
    }

    public static String swaggerToJson(Swagger swagger) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
        return objectMapper.writeValueAsString(swagger);
    }

}

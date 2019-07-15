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

package org.apache.james.json;

public class DTOModule<T, U extends DTO<T>> {

    public interface DTOConverter<T, U extends DTO<T>> {
        U convert(T domainObject, String typeName);
    }

    public interface ModuleFactory<T, U extends DTO<T>, ModuleTypeT extends DTOModule<T, U>> {
        ModuleTypeT create(DTOConverter<T, U> converter, Class<T> domainObjectType, Class<U> dtoType, String typeName);
    }

    public static <T> Builder<T> forDomainObject(Class<T> domainObjectType) {
        return new Builder<>(domainObjectType);
    }

    public static class Builder<T> {

        private final Class<T> type;

        public Builder(Class<T> type) {
            this.type = type;
        }

        public <U extends DTO<T>> RequireConversionFunctionBuilder<U> convertToDTO(Class<U> dtoType) {
            return new RequireConversionFunctionBuilder<>(dtoType);
        }

        public class RequireConversionFunctionBuilder<U extends DTO<T>> {

            private final Class<U> dtoType;

            private RequireConversionFunctionBuilder(Class<U> dtoType) {
                this.dtoType = dtoType;
            }

            public RequireTypeNameBuilder convertWith(DTOConverter<T, U> converter) {
                return new RequireTypeNameBuilder(converter);
            }

            public class RequireTypeNameBuilder {
                private final DTOConverter<T, U> converter;

                private RequireTypeNameBuilder(DTOConverter<T, U> converter) {
                    this.converter = converter;
                }

                public RequireModuleFactory typeName(String typeName) {
                    return new RequireModuleFactory(typeName);
                }

                public class RequireModuleFactory {

                    private final String typeName;

                    private RequireModuleFactory(String typeName) {
                        this.typeName = typeName;
                    }

                    public <ModuleTypeT extends DTOModule<T, U>> ModuleTypeT withFactory(ModuleFactory<T, U, ModuleTypeT> factory) {
                        return factory.create(converter, type, dtoType, typeName);
                    }
                }
            }
        }
    }

    private final DTOConverter<T, U> converter;
    private final Class<T> domainObjectType;
    private final Class<U> dtoType;
    private final String typeName;

    protected DTOModule(DTOConverter<T, U> converter, Class<T> domainObjectType, Class<U> dtoType, String typeName) {
        this.converter = converter;
        this.domainObjectType = domainObjectType;
        this.dtoType = dtoType;
        this.typeName = typeName;
    }

    public String getDomainObjectType() {
        return typeName;
    }

    public Class<U> getDTOClass() {
        return dtoType;
    }

    public Class<T> getDomainObjectClass() {
        return domainObjectType;
    }

    public U toDTO(T domainObject) {
        return converter.convert(domainObject, typeName);
    }
}

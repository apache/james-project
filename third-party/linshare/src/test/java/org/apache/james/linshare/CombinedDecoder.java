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

package org.apache.james.linshare;

import java.io.IOException;
import java.lang.reflect.Type;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;

public class CombinedDecoder implements Decoder {
    interface SingleTypeDecoder extends Decoder {
        Type handledType();
    }

    static class Builder {
        @FunctionalInterface
        public interface DefaultDecoder {
            ReadyToBuild defaultDecoder(Decoder decoder);
        }

        static class ReadyToBuild {
            private final Decoder defaultDecoder;
            private final ImmutableMap.Builder<Type, Decoder> decoders;

            ReadyToBuild(Decoder decoder) {
                this.defaultDecoder = decoder;
                this.decoders = ImmutableMap.builder();
            }

            ReadyToBuild registerSingleTypeDecoder(SingleTypeDecoder decoder) {
                Preconditions.checkNotNull(decoder);
                decoders.put(decoder.handledType(), decoder);
                return this;
            }

            CombinedDecoder build() {
                Preconditions.checkNotNull(defaultDecoder);
                return new CombinedDecoder(defaultDecoder, decoders.build());
            }
        }
    }

    static Builder.DefaultDecoder builder() {
        return Builder.ReadyToBuild::new;
    }

    private final Decoder defaultDecoder;
    private final ImmutableMap<Type, Decoder> decoders;

    private CombinedDecoder(Decoder defaultDecoder, ImmutableMap<Type, Decoder> decoders) {
        this.defaultDecoder = defaultDecoder;
        this.decoders = decoders;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        return decoders.getOrDefault(type, defaultDecoder)
            .decode(response, type);
    }
}

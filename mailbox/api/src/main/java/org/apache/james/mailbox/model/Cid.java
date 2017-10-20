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

package org.apache.james.mailbox.model;


import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class Cid {

    public static final StrictCidValidator DEFAULT_VALIDATOR = new StrictCidValidator();

    public interface CidTransformation {
        Optional<Cid> apply(CidValidator cidValidator, String value);
    }

    public static class Identity implements CidTransformation {
        @Override
        public Optional<Cid> apply(CidValidator cidValidator, String value) {
            return toCid(cidValidator, value);
        }
    }

    public static class Unwrap implements CidTransformation {
        @Override
        public  Optional<Cid> apply(CidValidator cidValidator, String value) {
            cidValidator.validate(value);
            if (isWrappedWithAngleBrackets(value)) {
                return unwrap(value, cidValidator);
            }
            return toCid(cidValidator, value);
        }


        private Optional<Cid> unwrap(String cidAsString, CidValidator cidValidator) {
            String unwrapCid = cidAsString.substring(1, cidAsString.length() - 1);
            return toCid(cidValidator, unwrapCid);
        }

        private boolean isWrappedWithAngleBrackets(String cidAsString) {
            return cidAsString != null
                && cidAsString.startsWith("<")
                && cidAsString.endsWith(">");
        }
    }

    public interface CidValidator {
        void validate(String cidValue);
    }

    public static class StrictCidValidator implements CidValidator {
        @Override
        public void validate(String cidValue) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(cidValue));
            Preconditions.checkArgument(!StringUtils.isBlank(cidValue), "'cidAsString' is mandatory");
        }
    }

    public static class RelaxedCidValidator implements CidValidator {
        @Override
        public void validate(String cidValue) {

        }
    }

    public static class CidParser {
        private Optional<CidValidator> validator;
        private Optional<CidTransformation> transformation;

        private CidParser() {
            validator = Optional.empty();
            transformation = Optional.empty();
        }

        public CidParser relaxed() {
            validator = Optional.<CidValidator>of(new RelaxedCidValidator());
            return this;
        }

        public CidParser strict() {
            validator = Optional.<CidValidator>of(new StrictCidValidator());
            return this;
        }

        public CidParser unwrap() {
            transformation = Optional.<CidTransformation>of(new Unwrap());
            return this;
        }

        public Optional<Cid> parse(String value) {
            CidValidator cidValidator = validator.orElse(DEFAULT_VALIDATOR);
            CidTransformation cidTransformation = transformation.orElse(new Identity());
            return cidTransformation.apply(cidValidator, value);
        }
    }

    public static CidParser parser() {
        return new CidParser();
    }

    public static Cid from(String value) {
        return parser()
            .strict()
            .unwrap()
            .parse(value)
            .get();
    }

    private static Optional<Cid> toCid(CidValidator cidValidator, String value) {
        cidValidator.validate(value);
        return toCid(value);
    }

    private static Optional<Cid> toCid(String cidAsString) {
        if (Strings.isNullOrEmpty(cidAsString) || StringUtils.isBlank(cidAsString)) {
            return Optional.empty();
        }
        return Optional.of(new Cid(cidAsString));
    }

    private final String value;

    private Cid(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
    
    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof Cid) {
            Cid other = (Cid) obj;
            return Objects.equal(this.value, other.value);
        }
        return false;
    }
    
    @Override
    public final int hashCode() {
        return Objects.hashCode(this.value);
    }
}

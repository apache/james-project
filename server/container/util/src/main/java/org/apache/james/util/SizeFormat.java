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

package org.apache.james.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.io.FileUtils;

import com.google.common.base.Preconditions;

public class SizeFormat {

    public enum Unit {
        TiB(FileUtils.ONE_TB_BI, "TiB"),
        GiB(FileUtils.ONE_GB_BI, "GiB"),
        MiB(FileUtils.ONE_MB_BI, "MiB"),
        KiB(FileUtils.ONE_KB_BI, "KiB"),
        Byte(BigInteger.valueOf(1), "bytes");

        static Unit locateUnit(long bytesCount) {
            if (bytesCount / FileUtils.ONE_TB > 0) {
                return TiB;
            }
            if (bytesCount / FileUtils.ONE_GB > 0) {
                return GiB;
            }
            if (bytesCount / FileUtils.ONE_MB > 0) {
                return  MiB;
            }
            if (bytesCount / FileUtils.ONE_KB > 0) {
                return  KiB;
            }
            return Byte;
        }

        private static final int SCALE = 2;
        private static final DecimalFormatSymbols DECIMAL_FORMAT_SYMBOLS = new DecimalFormatSymbols(Locale.US);
        private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##", DECIMAL_FORMAT_SYMBOLS);

        public static Optional<Unit> of(String rawValue) {
            return Arrays.stream(values())
                .filter(unit -> unit.notation.equals(rawValue))
                .findAny();
        }

        private final BigInteger bytesCount;
        private final String notation;

        Unit(BigInteger bytesCount, String notation) {
            this.bytesCount = bytesCount;
            this.notation = notation;
        }

        public long toByteCount(long value) {
            return bytesCount.multiply(BigInteger.valueOf(value))
                .longValueExact();
        }

        public String format(long size) {
            return format(new BigDecimal(size));
        }

        public String format(BigDecimal sizeAsDecimal) {
            return asString(scaleToUnit(sizeAsDecimal)) + " " + notation;
        }

        public BigDecimal scaleToUnit(BigDecimal sizeAsDecimal) {
            return sizeAsDecimal.divide(new BigDecimal(bytesCount), SCALE, RoundingMode.FLOOR);
        }

        private String asString(BigDecimal bigDecimal) {
            return DECIMAL_FORMAT.format(bigDecimal.doubleValue());
        }
    }

    public static String format(long bytesCount) {
        Preconditions.checkArgument(bytesCount >= 0, "Formatting of a negative size is forbidden");

        return Unit.locateUnit(bytesCount)
            .format(bytesCount);
    }

    public static long parseAsByteCount(String bytesWithUnit) {
        UnitParser.ParsingResult parsingResult = UnitParser.parse(bytesWithUnit);
        Unit unit = parsingResult.getUnit()
            .map(rawValue -> Unit.of(rawValue)
                .orElseThrow(() -> new IllegalArgumentException("Unknown unit " + rawValue)))
            .orElse(Unit.Byte);

        return unit.toByteCount(parsingResult.getNumber());
    }
}

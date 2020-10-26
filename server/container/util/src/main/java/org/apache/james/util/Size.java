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

import com.google.common.base.Preconditions;
import com.google.common.math.LongMath;

/**
 * This class is an helper for parsing integer input that may contain units.
 */
public class Size {

    public static final String UNKNOWN = "UNKNOWN";
    public static final String UNLIMITED = "UNLIMITED";
    public static final long UNKNOWN_VALUE = Long.MIN_VALUE;
    public static final long UNLIMITED_VALUE = -1;

    /**
     * supported units : B ( 2^0 ), K ( 2^10 ), M ( 2^20 ), G ( 2^30 )
     * See  RFC822.SIZE
     */
    public enum Unit {
        NoUnit,
        B,
        K,
        M,
        G
    }

    private static final long base = 1024;

    Unit unit;
    Long value;

    private Size(Unit unit, Long value) {
        this.unit = unit;
        this.value = value;
    }

    public static Size parse(String providedLongWithUnitString) throws Exception {
        if (providedLongWithUnitString.equalsIgnoreCase(UNKNOWN)) {
            return new Size(Unit.NoUnit, UNKNOWN_VALUE);
        }
        if (providedLongWithUnitString.equalsIgnoreCase(UNLIMITED)) {
            return new Size(Unit.NoUnit, UNLIMITED_VALUE);
        }
        char lastChar = providedLongWithUnitString.charAt(providedLongWithUnitString.length() - 1);
        Unit unit = getUnit(lastChar);
        String argWithoutUnit = removeLastCharIfNeeded(providedLongWithUnitString, unit);
        return new Size(unit, Long.parseLong(argWithoutUnit));
    }

    public static Size of(Long value, Unit unit) {
        Preconditions.checkArgument(value >= 0, "Maxsize must be positive");
        return new Size(unit, value);
    }

    public Unit getUnit() {
        return unit;
    }

    public Long getValue() {
        return value;
    }

    public long asBytes() {
        switch (unit) {
            case G:
                return value * LongMath.pow(base, 3);
            case M:
                return value * LongMath.pow(base, 2);
            case K:
                return value * LongMath.pow(base, 1);
            default:
                return value;
        }
    }

    private static String removeLastCharIfNeeded(String providedLongWithUnitString, Unit unit) {
        if (unit != Unit.NoUnit) {
            return providedLongWithUnitString.substring(0, providedLongWithUnitString.length() - 1);
        } else {
            return providedLongWithUnitString;
        }
    }

    private static Unit getUnit(char lastChar) throws Exception {
        switch (lastChar) {
            case 'K' :
            case 'k' :
                return Unit.K;
            case 'M' :
            case 'm' :
                return Unit.M;
            case 'G' :
            case 'g' :
                return Unit.G;
            case 'b' :
            case 'B' :
                return Unit.B;
            case '1' :
            case '2' :
            case '3' :
            case '4' :
            case '5' :
            case '6' :
            case '7' :
            case '8' :
            case '9' :
            case '0' :
                return Unit.NoUnit;
            default:
                throw new Exception("No unit corresponding to char : " + lastChar);
        }
    }

}
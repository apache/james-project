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

package org.apache.james.cli.utils;

import org.apache.james.mailbox.model.Quota;

/**
 * This class is an helper for parsing integer input that may contain units.
 */
public class ValueWithUnit {

    public static final String UNKNOWN = "UNKNOWN";
    public static final String UNLIMITED = "UNLIMITED";

    /**
     * supported units : B ( 2^0 ), K ( 2^10 ), M ( 2^20 ), G ( 2^30 )
     * See  RFC822.SIZE
     */
    private enum Unit {
        NoUnit,
        B,
        K,
        M,
        G
    }

    private static final long base = 1024;

    Unit unit;
    Long value;

    private ValueWithUnit(Unit unit, Long value) {
        this.unit = unit;
        this.value = value;
    }

    public static ValueWithUnit parse(String providedLongWithUnitString) throws Exception{
        if(providedLongWithUnitString.equalsIgnoreCase(UNKNOWN)) {
            return new ValueWithUnit(Unit.NoUnit, Quota.UNKNOWN);
        }
        if(providedLongWithUnitString.equalsIgnoreCase(UNLIMITED)) {
            return new ValueWithUnit(Unit.NoUnit, Quota.UNLIMITED);
        }
        char lastChar = providedLongWithUnitString.charAt(providedLongWithUnitString.length()-1);
        Unit unit = getUnit(lastChar);
        String argWithoutUnit = removeLastCharIfNeeded(providedLongWithUnitString, unit);
        return new ValueWithUnit(unit, Long.parseLong(argWithoutUnit));
    }

    public Unit getUnit() {
        return unit;
    }

    public Long getValue() {
        return value;
    }

    public long getConvertedValue() {
        switch (unit) {
            case G:
                value *= base;
            case M:
                value *= base;
            case K:
                value *= base;
            default:
                return value;
        }
    }

    private static String removeLastCharIfNeeded(String providedLongWithUnitString, Unit unit) {
        if(unit != Unit.NoUnit) {
            return providedLongWithUnitString.substring(0, providedLongWithUnitString.length()-1);
        } else {
            return providedLongWithUnitString;
        }
    }

    private static Unit getUnit(char lastChar) throws Exception{
        switch(lastChar) {
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
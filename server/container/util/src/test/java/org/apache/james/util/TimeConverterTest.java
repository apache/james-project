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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class TimeConverterTest {

    @Test 
    public void getMilliSecondsShouldConvertValueWhenMsecUnit() {
        //Given
        long expected = 2;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "msec");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
        
    @Test 
    public void getMilliSecondsShouldConvertValueWhenMsecAmountAsString() {
        //Given
        long expected = 2;
        //When
        long actual = TimeConverter.getMilliSeconds("2 msec");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
   
    @Test
    public void getMilliSecondsShouldConvertValueWhenMsecsUnit() {
        //Given
        long expected = 2;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "msecs");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void getMilliSecondsShouldConvertValueWhenMsecsAmountAsString() {
        //Given
        long expected = 2;
        //When
        long actual = TimeConverter.getMilliSeconds("2 msecs");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test 
    public void getMilliSecondsShouldConvertValueWhenSecUnit() { 
        //Given
        long expected = 2000;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "sec");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test 
    public void getMilliSecondsShouldConvertValueWhenSecAmountAsString() {
        //Given
        long expected = 2000;
        //When
        long actual = TimeConverter.getMilliSeconds("2 sec");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test 
    public void getMilliSecondsShouldConvertValueWhenSecsUnit() {
        long expected = 2000;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "secs");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test 
    public void getMilliSecondsShouldConvertValueWhenSecsAmountAsString() {
        //Given
        long expected = 2000;
        //When
        long actual = TimeConverter.getMilliSeconds("2 secs");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test 
    public void getMilliSecondsShouldConvertValueWhenMinuteUnit() {
        //Given
        long expected = 120000;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "minute");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
        
    @Test 
    public void getMilliSecondsShouldConvertValueWhenMinuteAmountAsString() {
        //Given
        long expected = 120000;
        //When
        long actual = TimeConverter.getMilliSeconds("2 minute");
        //Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void getMilliSecondsShouldConvertValueWhenMinutesUnit() {
        //Given
        long expected = 120000;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "minutes");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
        
    @Test 
    public void getMilliSecondsShouldConvertValueWhenMinutesAmountAsString() {
        //Given
        long expected = 120000;
        //When
        long actual = TimeConverter.getMilliSeconds("2 minutes");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void getMilliSecondsShouldConvertValueWhenHourUnit() {
        //Given
        long expected = 7200000;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "hour");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
        
    @Test
    public void getMilliSecondsShouldConvertValueWhenHourAmountAsString() {
        //Given
        long expected = 7200000;
        //When
        long actual = TimeConverter.getMilliSeconds("2 hour");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void getMilliSecondsShouldConvertValueWhenHoursUnit() {
        //Given
        long expected = 7200000;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "hours");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
        
    @Test 
    public void getMilliSecondsShouldConvertValueWhenHoursAmountAsString() {
        //Given
        long expected = 7200000;
        //When
        long actual = TimeConverter.getMilliSeconds("2 hours");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void getMilliSecondsShouldConvertValueWhenDayUnit() {
        //Given
        long expected = 172800000;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "day");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
        
    @Test
    public void getMilliSecondsShouldConvertValueWhenDayAmountAsString() {
        //Given
        long expected = 172800000;
        //When
        long actual = TimeConverter.getMilliSeconds("2 day");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void getMilliSecondsShouldConvertValueWhenDaysUnit() {
        //Given
        long expected = 172800000;
        //When
        long actual = TimeConverter.getMilliSeconds(2, "days");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
        
    @Test 
    public void getMilliSecondsShouldConvertValueWhenDaysAmountAsString() {
        //Given
        long expected = 172800000;
        //When
        long actual = TimeConverter.getMilliSeconds("2 days");
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test(expected = NumberFormatException.class) 
    public void getMilliSecondsShouldThrowWhenIllegalUnitInUnit() {
        TimeConverter.getMilliSeconds(2, "week");
    } 
    
    @Test(expected = NumberFormatException.class) 
    public void getMilliSecondsShouldThrowWhenIllegalUnitInRawString() { 
        TimeConverter.getMilliSeconds("2 week");
    } 

    @Test (expected = NumberFormatException.class)
    public void getMilliSecondsShouldThrowWhenIllegalPattern() {
        TimeConverter.getMilliSeconds("illegal pattern");
    }
}
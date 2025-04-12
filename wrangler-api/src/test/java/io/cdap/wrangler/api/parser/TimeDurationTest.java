/*
 * Copyright © 2017-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.wrangler.api.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TimeDurationTest {

  @Test
  public void testParseTimeUnits() {
    // Test nanoseconds
    TimeDuration duration = new TimeDuration("1000ns");
    assertEquals(1L, duration.getMilliseconds());
    
    // Test microseconds
    duration = new TimeDuration("1500us");
    assertEquals(1L, duration.getMilliseconds());
    
    // Test milliseconds
    duration = new TimeDuration("500ms");
    assertEquals(500L, duration.getMilliseconds());
    
    // Test seconds
    duration = new TimeDuration("2.5s");
    assertEquals(2500L, duration.getMilliseconds());
    
    // Test minutes
    duration = new TimeDuration("1.5m");
    assertEquals(90000L, duration.getMilliseconds());
    
    // Test hours
    duration = new TimeDuration("2h");
    assertEquals(7200000L, duration.getMilliseconds());
    
    // Test days
    duration = new TimeDuration("1d");
    assertEquals(86400000L, duration.getMilliseconds());
  }

  @Test
  public void testInvalidInputs() {
    // Test invalid unit
    assertThrows(IllegalArgumentException.class, () -> new TimeDuration("10xs"));
    
    // Test invalid number format
    assertThrows(IllegalArgumentException.class, () -> new TimeDuration("abcs"));
    
    // Test negative values
    assertThrows(IllegalArgumentException.class, () -> new TimeDuration("-1s"));
    
    // Test missing unit
    assertThrows(IllegalArgumentException.class, () -> new TimeDuration("10"));
  }

  @Test
  public void testValueAndType() {
    TimeDuration duration = new TimeDuration("1.5s");
    assertEquals("1.5s", duration.value());
    assertEquals(TokenType.TIME_DURATION, duration.type());
  }

  @Test
  public void testToJson() {
    TimeDuration duration = new TimeDuration("1.5s");
    assertEquals("{\"type\":\"TIME_DURATION\",\"value\":\"1.5s\"}", duration.toJson());
  }
} 
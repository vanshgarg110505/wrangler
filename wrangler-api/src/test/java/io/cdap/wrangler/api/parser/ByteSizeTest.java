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

public class ByteSizeTest {

  @Test
  public void testParseDecimalUnits() {
    // Test decimal units (base 1000)
    ByteSize size = new ByteSize("10B");
    assertEquals(10L, size.getBytes());
    
    size = new ByteSize("1.5KB");
    assertEquals(1500L, size.getBytes());
    
    size = new ByteSize("2MB");
    assertEquals(2_000_000L, size.getBytes());
    
    size = new ByteSize("3.5GB");
    assertEquals(3_500_000_000L, size.getBytes());
    
    size = new ByteSize("0.5TB");
    assertEquals(500_000_000_000L, size.getBytes());
  }

  @Test
  public void testParseBinaryUnits() {
    // Test binary units (base 1024)
    ByteSize size = new ByteSize("1KiB");
    assertEquals(1024L, size.getBytes());
    
    size = new ByteSize("2.5MiB");
    assertEquals((long)(2.5 * 1024 * 1024), size.getBytes());
    
    size = new ByteSize("3GiB");
    assertEquals(3L * 1024 * 1024 * 1024, size.getBytes());
    
    size = new ByteSize("0.5TiB");
    assertEquals((long)(0.5 * 1024 * 1024 * 1024 * 1024), size.getBytes());
  }

  @Test
  public void testInvalidInputs() {
    // Test invalid unit
    assertThrows(IllegalArgumentException.class, () -> new ByteSize("10XB"));
    
    // Test invalid number format
    assertThrows(IllegalArgumentException.class, () -> new ByteSize("abcMB"));
    
    // Test negative values
    assertThrows(IllegalArgumentException.class, () -> new ByteSize("-1MB"));
    
    // Test missing unit
    assertThrows(IllegalArgumentException.class, () -> new ByteSize("10"));
  }

  @Test
  public void testValueAndType() {
    ByteSize size = new ByteSize("1.5MB");
    assertEquals("1.5MB", size.value());
    assertEquals(TokenType.BYTE_SIZE, size.type());
  }

  @Test
  public void testToJson() {
    ByteSize size = new ByteSize("1.5MB");
    assertEquals("{\"type\":\"BYTE_SIZE\",\"value\":\"1.5MB\"}", size.toJson());
  }
} 
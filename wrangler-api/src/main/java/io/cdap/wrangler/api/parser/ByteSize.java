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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.cdap.wrangler.api.annotations.PublicEvolving;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a token for byte size values with units.
 * It supports both binary (KiB, MiB, etc.) and decimal (KB, MB, etc.) units.
 */
@PublicEvolving
public class ByteSize implements Token, Serializable {
  private static final long serialVersionUID = 1L;
  private static final Pattern BYTE_SIZE_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)([KMGTPEZY]?i?B)");
  
  private final double size;
  private final String unit;

  public ByteSize(String token) {
    Matcher matcher = BYTE_SIZE_PATTERN.matcher(token);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid byte size format: " + token);
    }
    this.size = Double.parseDouble(matcher.group(1));
    this.unit = matcher.group(2);
  }

  @Override
  public Object value() {
    return this;
  }

  @Override
  public TokenType type() {
    return TokenType.BYTE_SIZE;
  }

  @Override
  public JsonElement toJson() {
    JsonObject object = new JsonObject();
    object.addProperty("size", size);
    object.addProperty("unit", unit);
    object.addProperty("bytes", getBytes());
    return object;
  }

  public double getSize() {
    return size;
  }

  public String getUnit() {
    return unit;
  }

  /**
   * Returns the size in bytes.
   * For binary units (KiB, MiB, etc.), uses powers of 1024.
   * For decimal units (KB, MB, etc.), uses powers of 1000.
   *
   * @return size in bytes
   */
  public long getBytes() {
    double multiplier;
    if (unit.equals("B")) {
      return (long) size;
    }
    
    // Handle binary units (KiB, MiB, etc.)
    if (unit.startsWith("i")) {
      switch (unit.charAt(0)) {
        case 'K': multiplier = 1024L; break;
        case 'M': multiplier = 1024L * 1024L; break;
        case 'G': multiplier = 1024L * 1024L * 1024L; break;
        case 'T': multiplier = 1024L * 1024L * 1024L * 1024L; break;
        case 'P': multiplier = 1024L * 1024L * 1024L * 1024L * 1024L; break;
        case 'E': multiplier = 1024L * 1024L * 1024L * 1024L * 1024L * 1024L; break;
        case 'Z': multiplier = 1024L * 1024L * 1024L * 1024L * 1024L * 1024L * 1024L; break;
        case 'Y': multiplier = 1024L * 1024L * 1024L * 1024L * 1024L * 1024L * 1024L * 1024L; break;
        default: throw new IllegalStateException("Unsupported binary unit: " + unit);
      }
    } else {
      // Handle decimal units (KB, MB, etc.)
      switch (unit.charAt(0)) {
        case 'K': multiplier = 1000L; break;
        case 'M': multiplier = 1000L * 1000L; break;
        case 'G': multiplier = 1000L * 1000L * 1000L; break;
        case 'T': multiplier = 1000L * 1000L * 1000L * 1000L; break;
        case 'P': multiplier = 1000L * 1000L * 1000L * 1000L * 1000L; break;
        case 'E': multiplier = 1000L * 1000L * 1000L * 1000L * 1000L * 1000L; break;
        case 'Z': multiplier = 1000L * 1000L * 1000L * 1000L * 1000L * 1000L * 1000L; break;
        case 'Y': multiplier = 1000L * 1000L * 1000L * 1000L * 1000L * 1000L * 1000L * 1000L; break;
        default: throw new IllegalStateException("Unsupported decimal unit: " + unit);
      }
    }
    
    return (long) (size * multiplier);
  }

  @Override
  public String toString() {
    return size + unit;
  }
} 
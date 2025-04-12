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
 * This class represents a token for time duration values with units.
 * It supports various time units like seconds, minutes, hours, days, etc.
 */
@PublicEvolving
public class TimeDuration implements Token, Serializable {
  private static final long serialVersionUID = 1L;
  private static final Pattern TIME_DURATION_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(ns|us|ms|s|m|h|d|w|mo|y)");
  
  private final double duration;
  private final String unit;

  public TimeDuration(String token) {
    Matcher matcher = TIME_DURATION_PATTERN.matcher(token);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid time duration format: " + token);
    }
    this.duration = Double.parseDouble(matcher.group(1));
    this.unit = matcher.group(2);
  }

  @Override
  public String value() {
    return duration + unit;
  }

  @Override
  public TokenType type() {
    return TokenType.TIME_DURATION;
  }

  @Override
  public JsonElement toJson() {
    JsonObject object = new JsonObject();
    object.addProperty("duration", duration);
    object.addProperty("unit", unit);
    object.addProperty("milliseconds", getMilliseconds());
    return object;
  }

  public double getDuration() {
    return duration;
  }

  public String getUnit() {
    return unit;
  }

  /**
   * Returns the duration in milliseconds.
   * Converts from various time units to milliseconds.
   *
   * @return duration in milliseconds
   */
  public long getMilliseconds() {
    double multiplier;
    switch (unit) {
      case "ns": multiplier = 0.000001; break;  // nanoseconds to milliseconds
      case "us": multiplier = 0.001; break;     // microseconds to milliseconds
      case "ms": multiplier = 1.0; break;       // milliseconds
      case "s":  multiplier = 1000.0; break;    // seconds to milliseconds
      case "m":  multiplier = 60000.0; break;   // minutes to milliseconds
      case "h":  multiplier = 3600000.0; break; // hours to milliseconds
      case "d":  multiplier = 86400000.0; break; // days to milliseconds
      case "w":  multiplier = 604800000.0; break; // weeks to milliseconds
      case "mo": multiplier = 2592000000.0; break; // months (30 days) to milliseconds
      case "y":  multiplier = 31536000000.0; break; // years (365 days) to milliseconds
      default: throw new IllegalStateException("Unsupported time unit: " + unit);
    }
    
    return (long) (duration * multiplier);
  }

  @Override
  public String toString() {
    return duration + unit;
  }
} 
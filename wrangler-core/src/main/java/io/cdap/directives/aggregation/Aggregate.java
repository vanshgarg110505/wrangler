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

package io.cdap.directives.aggregation;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.lineage.Lineage;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.ColumnNameList;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.TimeDuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A directive for performing aggregation operations on byte sizes and time durations.
 * This directive supports aggregating byte sizes and time durations with configurable output units.
 */
@Plugin(type = Directive.TYPE)
@Name(Aggregate.NAME)
@Categories(categories = { "aggregation" })
@Description("Performs aggregation operations on byte sizes and time durations with configurable output units.")
public class Aggregate implements Directive, Lineage {
  public static final String NAME = "aggregate";
  
  private String sizeSourceColumn;
  private String timeSourceColumn;
  private String sizeTargetColumn;
  private String timeTargetColumn;
  private String sizeOutputUnit;
  private String timeOutputUnit;
  private AggregationType timeAggregationType;
  
  // Keys for storing accumulated values in ExecutorContext
  private static final String TOTAL_SIZE_KEY = "aggregate.total_size";
  private static final String TOTAL_TIME_KEY = "aggregate.total_time";
  private static final String ROW_COUNT_KEY = "aggregate.row_count";
  
  /**
   * Defines the supported aggregation types.
   */
  public enum AggregationType {
    TOTAL("total"),
    AVERAGE("average");
    
    private String name;
    
    AggregationType(String name) {
      this.name = name;
    }
    
    String getName() {
      return name;
    }
  }

  @Override
  public UsageDefinition define() {
    UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
    builder.define("sizeSource", TokenType.COLUMN_NAME);
    builder.define("timeSource", TokenType.COLUMN_NAME);
    builder.define("sizeTarget", TokenType.COLUMN_NAME);
    builder.define("timeTarget", TokenType.COLUMN_NAME);
    builder.define("sizeUnit", TokenType.TEXT, Optional.TRUE);
    builder.define("timeUnit", TokenType.TEXT, Optional.TRUE);
    builder.define("timeAggregation", TokenType.TEXT, Optional.TRUE);
    return builder.build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    this.sizeSourceColumn = args.value("sizeSource").value();
    this.timeSourceColumn = args.value("timeSource").value();
    this.sizeTargetColumn = args.value("sizeTarget").value();
    this.timeTargetColumn = args.value("timeTarget").value();
    
    // Parse optional size unit
    if (args.contains("sizeUnit")) {
      this.sizeOutputUnit = args.value("sizeUnit").value().toUpperCase();
      validateSizeUnit(sizeOutputUnit);
    } else {
      this.sizeOutputUnit = "B"; // Default to bytes
    }
    
    // Parse optional time unit
    if (args.contains("timeUnit")) {
      this.timeOutputUnit = args.value("timeUnit").value().toLowerCase();
      validateTimeUnit(timeOutputUnit);
    } else {
      this.timeOutputUnit = "s"; // Default to seconds
    }
    
    // Parse optional time aggregation type
    if (args.contains("timeAggregation")) {
      String aggType = args.value("timeAggregation").value().toUpperCase();
      try {
        this.timeAggregationType = AggregationType.valueOf(aggType);
      } catch (IllegalArgumentException e) {
        throw new DirectiveParseException(NAME,
          String.format("Invalid time aggregation type: %s. Supported types are: total, average", aggType));
      }
    } else {
      this.timeAggregationType = AggregationType.TOTAL; // Default to total
    }
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context) 
      throws DirectiveExecutionException {
    if (rows.isEmpty()) {
      return rows;
    }
    
    // Initialize or get accumulated values from context
    long totalSizeBytes = context.getProperty(TOTAL_SIZE_KEY, Long.class).orElse(0L);
    long totalTimeNanos = context.getProperty(TOTAL_TIME_KEY, Long.class).orElse(0L);
    int rowCount = context.getProperty(ROW_COUNT_KEY, Integer.class).orElse(0);
    
    // Process current batch of rows
    for (Row row : rows) {
      // Process size - convert to bytes
      Object sizeValue = row.getValue(sizeSourceColumn);
      if (sizeValue instanceof ByteSize) {
        ByteSize byteSize = (ByteSize) sizeValue;
        totalSizeBytes += byteSize.getBytes(); // ByteSize already stores value in bytes
      } else {
        throw new DirectiveExecutionException(NAME,
          String.format("Column '%s' does not contain byte size values", sizeSourceColumn));
      }
      
      // Process time - convert to nanoseconds
      Object timeValue = row.getValue(timeSourceColumn);
      if (timeValue instanceof TimeDuration) {
        TimeDuration timeDuration = (TimeDuration) timeValue;
        // Convert milliseconds to nanoseconds
        totalTimeNanos += timeDuration.getMilliseconds() * 1000000L;
      } else {
        throw new DirectiveExecutionException(NAME,
          String.format("Column '%s' does not contain time duration values", timeSourceColumn));
      }
      
      rowCount++;
    }
    
    // Store accumulated values back in context
    context.setProperty(TOTAL_SIZE_KEY, totalSizeBytes);
    context.setProperty(TOTAL_TIME_KEY, totalTimeNanos);
    context.setProperty(ROW_COUNT_KEY, rowCount);
    
    // If this is the last batch, return a single row with the final results
    if (context.isLastBatch()) {
      return createFinalResultRow(context);
    }
    
    // For non-final batches, return empty list since we're accumulating
    return new ArrayList<>();
  }

  /**
   * Creates a single row containing the final aggregated results.
   */
  private List<Row> createFinalResultRow(ExecutorContext context) {
    // Get final totals from context
    long totalSizeBytes = context.getProperty(TOTAL_SIZE_KEY, Long.class).orElse(0L);
    long totalTimeNanos = context.getProperty(TOTAL_TIME_KEY, Long.class).orElse(0L);
    int rowCount = context.getProperty(ROW_COUNT_KEY, Integer.class).orElse(0);
    
    // Convert total size to specified unit
    long convertedSize = convertSize(totalSizeBytes, sizeOutputUnit);
    
    // Convert total time to specified unit (convert from nanoseconds to target unit)
    double convertedTime = convertTime(totalTimeNanos / 1000000.0, timeOutputUnit);
    if (timeAggregationType == AggregationType.AVERAGE) {
      convertedTime = convertedTime / rowCount;
    }
    
    // Create a single row with the results
    Row resultRow = new Row();
    resultRow.add(sizeTargetColumn, convertedSize + sizeOutputUnit);
    resultRow.add(timeTargetColumn, convertedTime + timeOutputUnit);
    
    // Clear the context properties since we're done
    context.setProperty(TOTAL_SIZE_KEY, null);
    context.setProperty(TOTAL_TIME_KEY, null);
    context.setProperty(ROW_COUNT_KEY, null);
    
    List<Row> results = new ArrayList<>();
    results.add(resultRow);
    return results;
  }

  private void validateSizeUnit(String unit) throws DirectiveParseException {
    if (!unit.matches("B|KB|MB|GB|TB|PB|EB|ZB|YB|KiB|MiB|GiB|TiB|PiB|EiB|ZiB|YiB")) {
      throw new DirectiveParseException(NAME,
        String.format("Invalid size unit: %s. Supported units are: B, KB, MB, GB, TB, PB, EB, ZB, YB, KiB, MiB, GiB, TiB, PiB, EiB, ZiB, YiB", unit));
    }
  }

  private void validateTimeUnit(String unit) throws DirectiveParseException {
    if (!unit.matches("ns|us|ms|s|m|h|d|w|mo|y")) {
      throw new DirectiveParseException(NAME,
        String.format("Invalid time unit: %s. Supported units are: ns, us, ms, s, m, h, d, w, mo, y", unit));
    }
  }

  private long convertSize(long bytes, String targetUnit) {
    switch (targetUnit) {
      case "B": return bytes;
      case "KB": return bytes / 1000;
      case "MB": return bytes / (1000 * 1000);
      case "GB": return bytes / (1000 * 1000 * 1000);
      case "TB": return bytes / (1000L * 1000 * 1000 * 1000);
      case "PB": return bytes / (1000L * 1000 * 1000 * 1000 * 1000);
      case "EB": return bytes / (1000L * 1000 * 1000 * 1000 * 1000 * 1000);
      case "ZB": return bytes / (1000L * 1000 * 1000 * 1000 * 1000 * 1000 * 1000);
      case "YB": return bytes / (1000L * 1000 * 1000 * 1000 * 1000 * 1000 * 1000 * 1000);
      case "KiB": return bytes / 1024;
      case "MiB": return bytes / (1024 * 1024);
      case "GiB": return bytes / (1024 * 1024 * 1024);
      case "TiB": return bytes / (1024L * 1024 * 1024 * 1024);
      case "PiB": return bytes / (1024L * 1024 * 1024 * 1024 * 1024);
      case "EiB": return bytes / (1024L * 1024 * 1024 * 1024 * 1020);
      case "ZiB": return bytes / (1024L * 1024 * 1024 * 1024 * 1024 * 1024 * 1024);
      case "YiB": return bytes / (1024L * 1024 * 1024 * 1024 * 1024 * 1024 * 1024 * 1024);
      default: return bytes;
    }
  }

  private double convertTime(long milliseconds, String targetUnit) {
    switch (targetUnit) {
      case "ns": return milliseconds * 1000000.0;
      case "us": return milliseconds * 1000.0;
      case "ms": return milliseconds;
      case "s": return milliseconds / 1000.0;
      case "m": return milliseconds / (1000.0 * 60);
      case "h": return milliseconds / (1000.0 * 60 * 60);
      case "d": return milliseconds / (1000.0 * 60 * 60 * 24);
      case "w": return milliseconds / (1000.0 * 60 * 60 * 24 * 7);
      case "mo": return milliseconds / (1000.0 * 60 * 60 * 24 * 30);
      case "y": return milliseconds / (1000.0 * 60 * 60 * 24 * 365);
      default: return milliseconds;
    }
  }

  @Override
  public void destroy() {
    // no-op
  }

  @Override
  public Mutation lineage() {
    return Mutation.builder()
      .readable("Aggregated byte sizes from '%s' to '%s' and time durations from '%s' to '%s'", 
        sizeSourceColumn, sizeTargetColumn, timeSourceColumn, timeTargetColumn)
      .build();
  }
} 
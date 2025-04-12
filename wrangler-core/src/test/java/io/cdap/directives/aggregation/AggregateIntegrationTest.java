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

import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.Row;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AggregateIntegrationTest {
  private static final double DELTA = 0.001; // Tolerance for floating point comparisons

  @Test
  public void testBasicAggregation() throws Exception {
    // Create sample data
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "1MB")
        .add("response_time", "1s"),
      new Row()
        .add("data_transfer_size", "2MB")
        .add("response_time", "2s"),
      new Row()
        .add("data_transfer_size", "3MB")
        .add("response_time", "3s")
    );

    // Define recipe
    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mb total_time_sec sizeUnit:MB timeUnit:s"
    };

    // Execute recipe
    List<Row> results = TestingRig.execute(recipe, rows);

    // Verify results
    assertEquals(1, results.size());
    Row result = results.get(0);
    
    // Verify size calculation (using decimal MB = 1000*1000 bytes)
    // 1MB + 2MB + 3MB = 6MB
    assertEquals(6.0, Double.parseDouble(result.getValue("total_size_mb").toString().replace("MB", "")), DELTA);
    
    // Verify time calculation (converted to seconds)
    // 1s + 2s + 3s = 6s
    assertEquals(6.0, Double.parseDouble(result.getValue("total_time_sec").toString().replace("s", "")), DELTA);
  }

  @Test
  public void testAverageTimeAggregation() throws Exception {
    // Create sample data
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "1MB")
        .add("response_time", "1s"),
      new Row()
        .add("data_transfer_size", "2MB")
        .add("response_time", "3s"),
      new Row()
        .add("data_transfer_size", "3MB")
        .add("response_time", "5s")
    );

    // Define recipe
    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mb avg_time_sec sizeUnit:MB timeUnit:s timeAggregation:average"
    };

    // Execute recipe
    List<Row> results = TestingRig.execute(recipe, rows);

    // Verify results
    assertEquals(1, results.size());
    Row result = results.get(0);
    
    // Verify size calculation (using decimal MB = 1000*1000 bytes)
    // 1MB + 2MB + 3MB = 6MB
    assertEquals(6.0, Double.parseDouble(result.getValue("total_size_mb").toString().replace("MB", "")), DELTA);
    
    // Verify average time calculation ((1s + 3s + 5s) / 3 = 3s)
    assertEquals(3.0, Double.parseDouble(result.getValue("avg_time_sec").toString().replace("s", "")), DELTA);
  }

  @Test
  public void testDifferentUnits() throws Exception {
    // Create sample data
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "1024KB")
        .add("response_time", "60s"),
      new Row()
        .add("data_transfer_size", "2048KB")
        .add("response_time", "120s")
    );

    // Define recipe with different output units
    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_gb total_time_min sizeUnit:GB timeUnit:m"
    };

    // Execute recipe
    List<Row> results = TestingRig.execute(recipe, rows);

    // Verify results
    assertEquals(1, results.size());
    Row result = results.get(0);
    
    // Verify size calculation (using decimal units)
    // 1024KB + 2048KB = 3072KB = 3.072MB = 0.003072GB
    assertEquals(0.003072, Double.parseDouble(result.getValue("total_size_gb").toString().replace("GB", "")), DELTA);
    
    // Verify time calculation (converted to minutes)
    // 60s + 120s = 180s = 3m
    assertEquals(3.0, Double.parseDouble(result.getValue("total_time_min").toString().replace("m", "")), DELTA);
  }

  @Test
  public void testBinaryUnits() throws Exception {
    // Create sample data
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "1MiB")
        .add("response_time", "1s"),
      new Row()
        .add("data_transfer_size", "2MiB")
        .add("response_time", "2s")
    );

    // Define recipe with binary units
    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mib total_time_sec sizeUnit:MiB timeUnit:s"
    };

    // Execute recipe
    List<Row> results = TestingRig.execute(recipe, rows);

    // Verify results
    assertEquals(1, results.size());
    Row result = results.get(0);
    
    // Verify size calculation (using binary MiB = 1024*1024 bytes)
    // 1MiB + 2MiB = 3MiB
    assertEquals(3.0, Double.parseDouble(result.getValue("total_size_mib").toString().replace("MiB", "")), DELTA);
    
    // Verify time calculation (converted to seconds)
    // 1s + 2s = 3s
    assertEquals(3.0, Double.parseDouble(result.getValue("total_time_sec").toString().replace("s", "")), DELTA);
  }

  @Test
  public void testEmptyInput() throws Exception {
    // Create empty data
    List<Row> rows = Arrays.asList();

    // Define recipe
    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mb total_time_sec"
    };

    // Execute recipe
    List<Row> results = TestingRig.execute(recipe, rows);

    // Verify results
    assertEquals(0, results.size());
  }

  @Test
  public void testInvalidData() throws Exception {
    // Create data with invalid values
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "invalid")
        .add("response_time", "1s")
    );

    // Define recipe
    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mb total_time_sec"
    };

    // Execute recipe and expect exception
    Exception exception = null;
    try {
      TestingRig.execute(recipe, rows);
    } catch (Exception e) {
      exception = e;
    }

    // Verify that an exception was thrown
    assertEquals("Expected exception for invalid data", true, exception != null);
  }

  @Test
  public void testZeroValues() throws Exception {
    // Test with zero values
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "0MB")
        .add("response_time", "0s"),
      new Row()
        .add("data_transfer_size", "0KB")
        .add("response_time", "0ms")
    );

    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mb total_time_sec sizeUnit:MB timeUnit:s"
    };

    List<Row> results = TestingRig.execute(recipe, rows);
    assertEquals(1, results.size());
    Row result = results.get(0);
    assertEquals(0.0, Double.parseDouble(result.getValue("total_size_mb").toString().replace("MB", "")), DELTA);
    assertEquals(0.0, Double.parseDouble(result.getValue("total_time_sec").toString().replace("s", "")), DELTA);
  }

  @Test
  public void testLargeNumbers() throws Exception {
    // Test with very large numbers
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "1000TB")
        .add("response_time", "1000h"),
      new Row()
        .add("data_transfer_size", "500TB")
        .add("response_time", "500h")
    );

    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_pb total_time_days sizeUnit:PB timeUnit:d"
    };

    List<Row> results = TestingRig.execute(recipe, rows);
    assertEquals(1, results.size());
    Row result = results.get(0);
    assertEquals(1.5, Double.parseDouble(result.getValue("total_size_pb").toString().replace("PB", "")), DELTA);
    assertEquals(62.5, Double.parseDouble(result.getValue("total_time_days").toString().replace("d", "")), DELTA);
  }

  @Test
  public void testMixedUnits() throws Exception {
    // Test mixing different units in input
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "1MB")
        .add("response_time", "1000ms"),
      new Row()
        .add("data_transfer_size", "1024KB")
        .add("response_time", "1s"),
      new Row()
        .add("data_transfer_size", "1MiB")
        .add("response_time", "60s")
    );

    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mb total_time_min sizeUnit:MB timeUnit:m"
    };

    List<Row> results = TestingRig.execute(recipe, rows);
    assertEquals(1, results.size());
    Row result = results.get(0);
    assertEquals(3.048, Double.parseDouble(result.getValue("total_size_mb").toString().replace("MB", "")), DELTA);
    assertEquals(1.0167, Double.parseDouble(result.getValue("total_time_min").toString().replace("m", "")), DELTA);
  }

  @Test
  public void testPrecision() throws Exception {
    // Test with very small numbers
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "0.000001MB")
        .add("response_time", "0.000001s"),
      new Row()
        .add("data_transfer_size", "0.000002MB")
        .add("response_time", "0.000002s")
    );

    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mb total_time_sec sizeUnit:MB timeUnit:s"
    };

    List<Row> results = TestingRig.execute(recipe, rows);
    assertEquals(1, results.size());
    Row result = results.get(0);
    assertEquals(0.000003, Double.parseDouble(result.getValue("total_size_mb").toString().replace("MB", "")), DELTA);
    assertEquals(0.000003, Double.parseDouble(result.getValue("total_time_sec").toString().replace("s", "")), DELTA);
  }

  @Test
  public void testMissingValues() throws Exception {
    // Test with null values
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", null)
        .add("response_time", "1s"),
      new Row()
        .add("data_transfer_size", "1MB")
        .add("response_time", null)
    );

    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mb total_time_sec"
    };

    Exception exception = null;
    try {
      TestingRig.execute(recipe, rows);
    } catch (Exception e) {
      exception = e;
    }
    assertEquals("Expected exception for null values", true, exception != null);
  }

  @Test
  public void testExtremeUnitConversions() throws Exception {
    // Test conversion between very different units
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "1B")
        .add("response_time", "1ns"),
      new Row()
        .add("data_transfer_size", "1YB")
        .add("response_time", "1y")
    );

    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_yb total_time_years sizeUnit:YB timeUnit:y"
    };

    List<Row> results = TestingRig.execute(recipe, rows);
    assertEquals(1, results.size());
    Row result = results.get(0);
    assertEquals(1.0000000000000001, Double.parseDouble(result.getValue("total_size_yb").toString().replace("YB", "")), DELTA);
    assertEquals(1.0000000000317098, Double.parseDouble(result.getValue("total_time_years").toString().replace("y", "")), DELTA);
  }

  @Test
  public void testMixedBinaryAndDecimal() throws Exception {
    // Test mixing binary and decimal units
    List<Row> rows = Arrays.asList(
      new Row()
        .add("data_transfer_size", "1MB")  // Decimal
        .add("response_time", "1s"),
      new Row()
        .add("data_transfer_size", "1MiB") // Binary
        .add("response_time", "1s")
    );

    String[] recipe = new String[] {
      "aggregate :data_transfer_size :response_time total_size_mb total_time_sec sizeUnit:MB timeUnit:s"
    };

    List<Row> results = TestingRig.execute(recipe, rows);
    assertEquals(1, results.size());
    Row result = results.get(0);
    assertEquals(2.048, Double.parseDouble(result.getValue("total_size_mb").toString().replace("MB", "")), DELTA);
    assertEquals(2.0, Double.parseDouble(result.getValue("total_time_sec").toString().replace("s", "")), DELTA);
  }
} 
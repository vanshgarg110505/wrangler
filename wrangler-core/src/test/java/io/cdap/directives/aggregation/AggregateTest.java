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

import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.TimeDuration;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class AggregateTest {
  private Aggregate directive;
  private ExecutorContext context;
  
  @Before
  public void setUp() {
    directive = new Aggregate();
    context = new TestExecutorContext();
  }
  
  @Test
  public void testInitialize() throws DirectiveParseException {
    directive.initialize(new TestArguments()
      .add("sizeSource", "file_size")
      .add("timeSource", "duration")
      .add("sizeTarget", "total_size")
      .add("timeTarget", "total_time")
      .add("sizeUnit", "MB")
      .add("timeUnit", "s")
      .add("timeAggregation", "total"));
  }
  
  @Test
  public void testInitializeWithDefaults() throws DirectiveParseException {
    directive.initialize(new TestArguments()
      .add("sizeSource", "file_size")
      .add("timeSource", "duration")
      .add("sizeTarget", "total_size")
      .add("timeTarget", "total_time"));
  }
  
  @Test
  public void testInvalidSizeUnit() {
    assertThrows(DirectiveParseException.class, () -> 
      directive.initialize(new TestArguments()
        .add("sizeSource", "file_size")
        .add("timeSource", "duration")
        .add("sizeTarget", "total_size")
        .add("timeTarget", "total_time")
        .add("sizeUnit", "XB")));
  }
  
  @Test
  public void testInvalidTimeUnit() {
    assertThrows(DirectiveParseException.class, () -> 
      directive.initialize(new TestArguments()
        .add("sizeSource", "file_size")
        .add("timeSource", "duration")
        .add("sizeTarget", "total_size")
        .add("timeTarget", "total_time")
        .add("timeUnit", "xs")));
  }
  
  @Test
  public void testInvalidTimeAggregation() {
    assertThrows(DirectiveParseException.class, () -> 
      directive.initialize(new TestArguments()
        .add("sizeSource", "file_size")
        .add("timeSource", "duration")
        .add("sizeTarget", "total_size")
        .add("timeTarget", "total_time")
        .add("timeAggregation", "invalid")));
  }
  
  @Test
  public void testExecuteSingleBatch() throws DirectiveParseException, DirectiveExecutionException {
    directive.initialize(new TestArguments()
      .add("sizeSource", "file_size")
      .add("timeSource", "duration")
      .add("sizeTarget", "total_size")
      .add("timeTarget", "total_time")
      .add("sizeUnit", "MB")
      .add("timeUnit", "s"));
    
    List<Row> rows = new ArrayList<>();
    rows.add(new Row()
      .add("file_size", new ByteSize("1MB"))
      .add("duration", new TimeDuration("1s")));
    rows.add(new Row()
      .add("file_size", new ByteSize("2MB"))
      .add("duration", new TimeDuration("2s")));
    
    context.setLastBatch(true);
    List<Row> results = directive.execute(rows, context);
    
    assertEquals(1, results.size());
    Row result = results.get(0);
    assertEquals("3MB", result.getValue("total_size"));
    assertEquals("3.0s", result.getValue("total_time"));
  }
  
  @Test
  public void testExecuteMultipleBatches() throws DirectiveParseException, DirectiveExecutionException {
    directive.initialize(new TestArguments()
      .add("sizeSource", "file_size")
      .add("timeSource", "duration")
      .add("sizeTarget", "total_size")
      .add("timeTarget", "total_time")
      .add("sizeUnit", "MB")
      .add("timeUnit", "s"));
    
    // First batch
    List<Row> batch1 = new ArrayList<>();
    batch1.add(new Row()
      .add("file_size", new ByteSize("1MB"))
      .add("duration", new TimeDuration("1s")));
    batch1.add(new Row()
      .add("file_size", new ByteSize("2MB"))
      .add("duration", new TimeDuration("2s")));
    
    context.setLastBatch(false);
    List<Row> results1 = directive.execute(batch1, context);
    assertEquals(0, results1.size()); // No results during accumulation
    
    // Second batch
    List<Row> batch2 = new ArrayList<>();
    batch2.add(new Row()
      .add("file_size", new ByteSize("3MB"))
      .add("duration", new TimeDuration("3s")));
    
    context.setLastBatch(true);
    List<Row> results2 = directive.execute(batch2, context);
    
    assertEquals(1, results2.size());
    Row result = results2.get(0);
    assertEquals("6MB", result.getValue("total_size"));
    assertEquals("6.0s", result.getValue("total_time"));
  }
  
  @Test
  public void testAverageTimeAggregation() throws DirectiveParseException, DirectiveExecutionException {
    directive.initialize(new TestArguments()
      .add("sizeSource", "file_size")
      .add("timeSource", "duration")
      .add("sizeTarget", "total_size")
      .add("timeTarget", "total_time")
      .add("sizeUnit", "MB")
      .add("timeUnit", "s")
      .add("timeAggregation", "average"));
    
    List<Row> rows = new ArrayList<>();
    rows.add(new Row()
      .add("file_size", new ByteSize("1MB"))
      .add("duration", new TimeDuration("1s")));
    rows.add(new Row()
      .add("file_size", new ByteSize("2MB"))
      .add("duration", new TimeDuration("3s")));
    
    context.setLastBatch(true);
    List<Row> results = directive.execute(rows, context);
    
    assertEquals(1, results.size());
    Row result = results.get(0);
    assertEquals("3MB", result.getValue("total_size"));
    assertEquals("2.0s", result.getValue("total_time")); // Average of 1s and 3s
  }
  
  @Test
  public void testInvalidSizeValue() throws DirectiveParseException {
    directive.initialize(new TestArguments()
      .add("sizeSource", "file_size")
      .add("timeSource", "duration")
      .add("sizeTarget", "total_size")
      .add("timeTarget", "total_time"));
    
    List<Row> rows = new ArrayList<>();
    rows.add(new Row()
      .add("file_size", "not_a_byte_size")
      .add("duration", new TimeDuration("1s")));
    
    assertThrows(DirectiveExecutionException.class, () -> 
      directive.execute(rows, context));
  }
  
  @Test
  public void testInvalidTimeValue() throws DirectiveParseException {
    directive.initialize(new TestArguments()
      .add("sizeSource", "file_size")
      .add("timeSource", "duration")
      .add("sizeTarget", "total_size")
      .add("timeTarget", "total_time"));
    
    List<Row> rows = new ArrayList<>();
    rows.add(new Row()
      .add("file_size", new ByteSize("1MB"))
      .add("duration", "not_a_time_duration"));
    
    assertThrows(DirectiveExecutionException.class, () -> 
      directive.execute(rows, context));
  }
  
  // Helper class for testing
  private static class TestExecutorContext implements ExecutorContext {
    private boolean lastBatch = false;
    private final java.util.Map<String, Object> properties = new java.util.HashMap<>();
    
    @Override
    public boolean isLastBatch() {
      return lastBatch;
    }
    
    public void setLastBatch(boolean lastBatch) {
      this.lastBatch = lastBatch;
    }
    
    @Override
    public <T> java.util.Optional<T> getProperty(String key, Class<T> type) {
      return java.util.Optional.ofNullable(type.cast(properties.get(key)));
    }
    
    @Override
    public void setProperty(String key, Object value) {
      properties.put(key, value);
    }
  }
  
  // Helper class for testing
  private static class TestArguments implements io.cdap.wrangler.api.Arguments {
    private final java.util.Map<String, io.cdap.wrangler.api.parser.Token> args = new java.util.HashMap<>();
    
    public TestArguments add(String name, String value) {
      args.put(name, new io.cdap.wrangler.api.parser.Text(value));
      return this;
    }
    
    @Override
    public io.cdap.wrangler.api.parser.Token value(String name) {
      return args.get(name);
    }
    
    @Override
    public boolean contains(String name) {
      return args.containsKey(name);
    }
  }
} 
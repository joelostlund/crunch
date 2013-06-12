/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.crunch.io.hbase;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.crunch.Pair;
import org.apache.crunch.SourceTarget;
import org.apache.crunch.TableSource;
import org.apache.crunch.impl.mr.run.CrunchMapper;
import org.apache.crunch.io.CrunchInputs;
import org.apache.crunch.io.FormatBundle;
import org.apache.crunch.types.PTableType;
import org.apache.crunch.types.PType;
import org.apache.crunch.types.writable.Writables;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableInputFormat;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.util.Base64;
import org.apache.hadoop.mapreduce.Job;

public class HBaseSourceTarget extends HBaseTarget implements SourceTarget<Pair<ImmutableBytesWritable, Result>>,
    TableSource<ImmutableBytesWritable, Result> {

  private static final Log LOG = LogFactory.getLog(HBaseSourceTarget.class);
  
  private static final PTableType<ImmutableBytesWritable, Result> PTYPE = Writables.tableOf(
      Writables.writables(ImmutableBytesWritable.class), Writables.writables(Result.class));

  protected Scan scan;
  private FormatBundle<TableInputFormat> inputBundle;
  
  public HBaseSourceTarget(String table, Scan scan) {
    super(table);
    this.scan = scan;
    try {
      this.inputBundle = FormatBundle.forInput(TableInputFormat.class)
          .set(TableInputFormat.INPUT_TABLE, table)
          .set(TableInputFormat.SCAN, convertScanToString(scan));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public PType<Pair<ImmutableBytesWritable, Result>> getType() {
    return PTYPE;
  }

  @Override
  public PTableType<ImmutableBytesWritable, Result> getTableType() {
    return PTYPE;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof HBaseSourceTarget)) {
      return false;
    }
    HBaseSourceTarget o = (HBaseSourceTarget) other;
    // XXX scan does not have equals method
    return inputBundle.equals(o.inputBundle);
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(table).append(scan).toHashCode();
  }

  @Override
  public String toString() {
    return "HBaseTable(" + table + ")";
  }

  @Override
  public void configureSource(Job job, int inputId) throws IOException {
    Configuration conf = job.getConfiguration();
    HBaseConfiguration.addHbaseResources(conf);
    TableMapReduceUtil.addDependencyJars(job);
    if (inputId == -1) {
      job.setMapperClass(CrunchMapper.class);
      job.setInputFormatClass(inputBundle.getFormatClass());
      inputBundle.configure(job.getConfiguration());
    } else {
      Path dummy = new Path("/hbase/" + table);
      CrunchInputs.addInputPath(job, dummy, inputBundle, inputId);
    }
  }

  static String convertScanToString(Scan scan) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(out);
    scan.write(dos);
    return Base64.encodeBytes(out.toByteArray());
  }

  @Override
  public long getSize(Configuration conf) {
    // TODO something smarter here.
    return 1000L * 1000L * 1000L;
  }

  @Override
  public long getLastModifiedAt(Configuration configuration) {
    LOG.warn("Cannot determine last modified time for source: " + toString());
    return -1;
  }
}

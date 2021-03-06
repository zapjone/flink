/*
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

package org.apache.flink.table.planner.plan.nodes.exec.common;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.io.InputFormat;
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.api.dag.Transformation;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.table.connector.source.DataStreamScanProvider;
import org.apache.flink.table.connector.source.InputFormatProvider;
import org.apache.flink.table.connector.source.ScanTableSource;
import org.apache.flink.table.connector.source.SourceFunctionProvider;
import org.apache.flink.table.connector.source.SourceProvider;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.runtime.connector.source.ScanRuntimeProviderContext;
import org.apache.flink.table.runtime.typeutils.InternalTypeInfo;
import org.apache.flink.table.types.logical.RowType;

/**
 * Base exec node to read data from an external source defined by a {@link ScanTableSource}.
 */
public interface CommonExecTableSourceScan {

	default Transformation<RowData> createSourceTransformation(
			StreamExecutionEnvironment env,
			ScanTableSource tableSource,
			RowType outputType,
			String name) {
		InternalTypeInfo<RowData> outputTypeInfo = InternalTypeInfo.of(outputType);
		ScanTableSource.ScanRuntimeProvider provider =
				tableSource.getScanRuntimeProvider(ScanRuntimeProviderContext.INSTANCE);
		if (provider instanceof SourceFunctionProvider) {
			SourceFunction<RowData> sourceFunction = ((SourceFunctionProvider) provider).createSourceFunction();
			return env.addSource(sourceFunction, name, outputTypeInfo).getTransformation();
		} else if (provider instanceof InputFormatProvider) {
			InputFormat<RowData, ?> inputFormat = ((InputFormatProvider) provider).createInputFormat();
			return createInputFormatTransformation(env, inputFormat, outputTypeInfo, name);
		} else if (provider instanceof SourceProvider) {
			Source<RowData, ?, ?> source = ((SourceProvider) provider).createSource();
			// TODO: Push down watermark strategy to source scan
			return env.fromSource(source, WatermarkStrategy.noWatermarks(), name).getTransformation();
		} else if (provider instanceof DataStreamScanProvider) {
			return ((DataStreamScanProvider) provider).produceDataStream(env).getTransformation();
		} else {
			throw new UnsupportedOperationException(provider.getClass().getSimpleName() + " is unsupported now.");
		}
	}

	/**
	 * Creates a {@link Transformation} based on the given {@link InputFormat}.
	 * The implementation is different for streaming mode and batch mode.
	 */
	Transformation<RowData> createInputFormatTransformation(
			StreamExecutionEnvironment env,
			InputFormat<RowData, ?> inputFormat,
			InternalTypeInfo<RowData> outputTypeInfo,
			String name);

}

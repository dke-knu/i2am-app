/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.storm.starter.streams;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.starter.spout.RandomIntegerSpout;
import org.apache.storm.streams.Pair;
import org.apache.storm.streams.PairStream;
import org.apache.storm.streams.Stream;
import org.apache.storm.streams.StreamBuilder;
import org.apache.storm.streams.operations.mappers.TupleValueMappers;
import org.apache.storm.streams.tuple.Tuple3;
import org.apache.storm.streams.windowing.TumblingWindows;
import org.apache.storm.utils.Utils;

import static org.apache.storm.topology.base.BaseWindowedBolt.Count;

/**
 * An example that illustrates the usage of typed tuples (TupleN<..>) and {@link TupleValueMappers}.
 */
public class TypedTupleExample {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        StreamBuilder builder = new StreamBuilder();
        /**
         * The spout emits sequences of (Integer, Long, Long). TupleValueMapper can be used to extract fields
         * from the values and produce a stream of typed tuple (Tuple3<Integer, Long, Long> in this case.
         */
        Stream<Tuple3<Integer, Long, Long>> stream = builder.newStream(new RandomIntegerSpout(), TupleValueMappers.of(0, 1, 2));

        PairStream<Long, Integer> pairs = stream.mapToPair(t -> Pair.of(t._2 / 10000, t._1));

        pairs.window(TumblingWindows.of(Count.of(10))).groupByKey().print();

        Config config = new Config();
        if (args.length > 0) {
            config.setNumWorkers(1);
            StormSubmitter.submitTopologyWithProgressBar(args[0], config, builder.build());
        } else {
            try (LocalCluster cluster = new LocalCluster();
                 LocalCluster.LocalTopology topo = cluster.submitTopology("test", config, builder.build())) {
                Utils.sleep(60_000);
            }
        }
    }
}

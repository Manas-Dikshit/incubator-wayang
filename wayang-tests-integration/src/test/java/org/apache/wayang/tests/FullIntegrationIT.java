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

package org.apache.wayang.tests;

import org.apache.wayang.basic.WayangBasics;
import org.apache.wayang.basic.data.Record;
import org.apache.wayang.basic.data.Tuple2;
import org.apache.wayang.basic.operators.CollectionSource;
import org.apache.wayang.basic.operators.LocalCallbackSink;
import org.apache.wayang.basic.operators.MapOperator;
import org.apache.wayang.basic.operators.MaterializedGroupByOperator;
import org.apache.wayang.core.api.Configuration;
import org.apache.wayang.core.api.WayangContext;
import org.apache.wayang.core.api.exception.WayangException;
import org.apache.wayang.core.function.TransformationDescriptor;
import org.apache.wayang.core.plan.wayangplan.Operator;
import org.apache.wayang.core.plan.wayangplan.WayangPlan;
import org.apache.wayang.core.types.DataSetType;
import org.apache.wayang.core.types.DataUnitType;
import org.apache.wayang.core.util.WayangArrays;
import org.apache.wayang.core.util.WayangCollections;
import org.apache.wayang.flink.Flink;
import org.apache.wayang.java.Java;
import org.apache.wayang.spark.Spark;
import org.apache.wayang.sqlite3.Sqlite3;
import org.apache.wayang.tests.platform.MyMadeUpPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test the Java integration with Wayang.
 */
class FullIntegrationIT {

    private Configuration configuration;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        this.configuration = new Configuration();
        File sqlite3dbFile = File.createTempFile("wayang-sqlite3", "db");
        sqlite3dbFile.deleteOnExit();
        this.configuration.setProperty(
                "wayang.sqlite3.jdbc.url",
                "jdbc:sqlite:" + sqlite3dbFile.getAbsolutePath()
        );
        WayangPlans.prepareSqlite3Scenarios(this.configuration);
    }

    @Test
    void testReadAndWrite() throws IOException {
        // Build a Wayang plan.
        List<String> collector = new LinkedList<>();
        WayangPlan wayangPlan = WayangPlans.readWrite(WayangPlans.FILE_SOME_LINES_TXT, collector);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin())
                .with(Flink.basicPlugin());

        // Have Wayang execute the plan.
        wayangContext.execute(wayangPlan);

        // Verify the plan result.
        final List<String> lines = Files.lines(Paths.get(WayangPlans.FILE_SOME_LINES_TXT)).collect(Collectors.toList());
        assertEquals(lines, collector);
    }

    @Test
    void testReadAndWriteCrossPlatform() throws IOException {
        // Build a Wayang plan.
        List<String> collector = new LinkedList<>();
        WayangPlan wayangPlan = WayangPlans.readWrite(WayangPlans.FILE_SOME_LINES_TXT, collector);
        final Operator sink = wayangPlan.getSinks().stream().findFirst().get();
        sink.addTargetPlatform(Spark.platform());
        final Operator source = sink.getEffectiveOccupant(0).getOwner();
        source.addTargetPlatform(Java.platform());

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        // Have Wayang execute the plan.
        wayangContext.execute(wayangPlan);

        // Verify the plan result.
        final List<String> lines = Files.lines(Paths.get(WayangPlans.FILE_SOME_LINES_TXT)).collect(Collectors.toList());
        assertEquals(lines, collector);
    }

    @Test
    void testReadAndTransformAndWrite() {
        // Build a Wayang plan.
        final WayangPlan wayangPlan = WayangPlans.readTransformWrite(WayangPlans.FILE_SOME_LINES_TXT);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        // Have Wayang execute the plan.
        wayangContext.execute(wayangPlan);
    }

    @Test
    void testReadAndTransformAndWriteWithIllegalConfiguration1() {
        final WayangPlan wayangPlan = WayangPlans.readTransformWrite(WayangPlans.FILE_SOME_LINES_TXT);
        wayangPlan.getSinks().forEach(sink -> sink.addTargetPlatform(MyMadeUpPlatform.getInstance()));
        WayangContext wayangContext = new WayangContext(configuration)
                    .with(Java.basicPlugin())
                    .with(Spark.basicPlugin());
        assertThrows(WayangException.class, () ->

            // Have Wayang execute the plan.
            wayangContext.execute(wayangPlan));

    }

    @Test
    void testMultiSourceAndMultiSink() {
        // Define some input data.
        final List<String> collection1 = Arrays.asList("This is source 1.", "This is source 1, too.");
        final List<String> collection2 = Arrays.asList("This is source 2.", "This is source 2, too.");
        List<String> collector1 = new LinkedList<>();
        List<String> collector2 = new LinkedList<>();
        final WayangPlan wayangPlan = WayangPlans.multiSourceMultiSink(collection1, collection2, collector1, collector2);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        // Have Wayang execute the plan.
        wayangContext.execute(wayangPlan);

        // Check the results in both sinks.
        List<String> expectedOutcome1 = Stream.concat(collection1.stream(), collection2.stream())
                .map(String::toUpperCase)
                .collect(Collectors.toList());
        List<String> expectedOutcome2 = Stream.concat(collection1.stream(), collection2.stream())
                .collect(Collectors.toList());
        Collections.sort(expectedOutcome1);
        Collections.sort(expectedOutcome2);
        Collections.sort(collector1);
        Collections.sort(collector2);
        assertEquals(expectedOutcome1, collector1);
        assertEquals(expectedOutcome2, collector2);
    }

    @Test
    void testMultiSourceAndHoleAndMultiSink() {
        // Define some input data.
        final List<String> collection1 = Arrays.asList("This is source 1.", "This is source 1, too.");
        final List<String> collection2 = Arrays.asList("This is source 2.", "This is source 2, too.");
        List<String> collector1 = new LinkedList<>();
        List<String> collector2 = new LinkedList<>();
        final WayangPlan wayangPlan = WayangPlans.multiSourceHoleMultiSink(collection1, collection2, collector1, collector2);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        // Have Wayang execute the plan.
        wayangContext.execute(wayangPlan);

        // Check the results in both sinks.
        List<String> expectedOutcome = Stream.concat(collection1.stream(), collection2.stream())
                .flatMap(string -> Stream.of(string.toLowerCase(), string.toUpperCase())).sorted().collect(Collectors.toList());
        Collections.sort(collector1);
        Collections.sort(collector2);
        assertEquals(expectedOutcome, collector1);
        assertEquals(expectedOutcome, collector2);
    }

    @Test
    void testGlobalMaterializedGroup() {
        // Build the WayangPlan.
        List<Iterable<Integer>> collector = new LinkedList<>();
        WayangPlan wayangPlan = WayangPlans.globalMaterializedGroup(collector, 1, 2, 3);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);

        assertEquals(1, collector.size());
        assertEquals(WayangCollections.asSet(1, 2, 3), WayangCollections.asCollection(collector.get(0), HashSet::new));
    }

    @Test
    void testIntersect() {
        // Build the WayangPlan.
        List<Integer> collector = new LinkedList<>();
        WayangPlan wayangPlan = WayangPlans.intersectSquares(collector, 0, 1, 2, 3, 3, -1, -1, -2, -3, -3, -4);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);

        assertEquals(WayangCollections.asSet(1, 4, 9), WayangCollections.asSet(collector));
    }

    @Test
    void testRepeat() {
        // Build the WayangPlan.
        List<Integer> collector = new LinkedList<>();
        WayangPlan wayangPlan = WayangPlans.repeat(collector, 5, 0, 10, 20, 30, 45);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);

        assertEquals(5, collector.size());
        assertEquals(WayangCollections.asSet(5, 15, 25, 35, 50), WayangCollections.asSet(collector));
    }

    @Test
    void testPageRankWithGraphBasic() {
        // Build the WayangPlan.
        List<Tuple2<Long, Long>> edges = Arrays.asList(
                new Tuple2<>(0L, 1L),
                new Tuple2<>(0L, 2L),
                new Tuple2<>(0L, 3L),
                new Tuple2<>(1L, 2L),
                new Tuple2<>(1L, 3L),
                new Tuple2<>(2L, 3L),
                new Tuple2<>(3L, 0L)
        );
        List<Tuple2<Long, Float>> pageRanks = new LinkedList<>();
        WayangPlan wayangPlan = WayangPlans.pageRank(edges, pageRanks);

        // Execute the plan with a certain backend.
        WayangContext wayangContext = new WayangContext()
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin())
                .with(WayangBasics.graphPlugin());
        wayangContext.execute(wayangPlan);

        // Check the results.
        pageRanks.sort((r1, r2) -> Float.compare(r2.getField1(), r1.getField1()));
        final List<Long> vertexOrder = pageRanks.stream().map(Tuple2::getField0).collect(Collectors.toList());
        assertEquals(
                Arrays.asList(3L, 0L, 2L, 1L),
                vertexOrder
        );
    }

    @Test
    void testMapPartitions() {
        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext().with(Java.basicPlugin()).with(Spark.basicPlugin());

        // Execute the Wayang plan.
        final Collection<Tuple2<String, Integer>> result = WayangPlans.mapPartitions(wayangContext, 0, 1, 1, 3, 3, 4, 4, 5, 5, 6);

        assertEquals(
                WayangCollections.asSet(new Tuple2<>("even", 4), new Tuple2<>("odd", 6)),
                WayangCollections.asSet(result)
        );
    }

    @Test
    void testZipWithId() {
        // Build the WayangPlan.
        List<Long> collector = new LinkedList<>();
        WayangPlan wayangPlan = WayangPlans.zipWithId(collector, 0, 10, 20, 30, 30);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);

        assertEquals(1, collector.size());
        assertEquals(Long.valueOf(5L), collector.get(0));
    }

    @Test
    void testDiverseScenario1() {
        // Build the WayangPlan.
        WayangPlan wayangPlan = WayangPlans.diverseScenario1(WayangPlans.FILE_SOME_LINES_TXT);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);
    }

    @Test
    void testDiverseScenario2() {
        // Build the WayangPlan.
        WayangPlan wayangPlan = WayangPlans.diverseScenario2(WayangPlans.FILE_SOME_LINES_TXT, WayangPlans.FILE_OTHER_LINES_TXT);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);
    }

    @Test
    void testDiverseScenario3() {
        // Build the WayangPlan.
        WayangPlan wayangPlan = WayangPlans.diverseScenario2(WayangPlans.FILE_SOME_LINES_TXT, WayangPlans.FILE_OTHER_LINES_TXT);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);
    }

    @Test
    void testDiverseScenario4() {
        // Build the WayangPlan.
        WayangPlan wayangPlan = WayangPlans.diverseScenario4(WayangPlans.FILE_SOME_LINES_TXT, WayangPlans.FILE_OTHER_LINES_TXT);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);
    }

    @Test
    void testSimpleSingleStageLoop() {
        // Build the WayangPlan.
        final Set<Integer> collector = new HashSet<>();
        WayangPlan wayangPlan = WayangPlans.simpleLoop(3, collector, 0, 1, 2);

        wayangPlan.collectTopLevelOperatorByName("source").addTargetPlatform(Spark.platform());
        wayangPlan.collectTopLevelOperatorByName("convergenceSource").addTargetPlatform(Spark.platform());
        wayangPlan.collectTopLevelOperatorByName("loop").addTargetPlatform(Java.platform());
        wayangPlan.collectTopLevelOperatorByName("step").addTargetPlatform(Java.platform());
        wayangPlan.collectTopLevelOperatorByName("counter").addTargetPlatform(Java.platform());
        wayangPlan.collectTopLevelOperatorByName("sink").addTargetPlatform(Spark.platform());

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);

        final HashSet<Integer> expected = new HashSet<>(WayangArrays.asList(WayangArrays.range(0, 24)));
        assertEquals(expected, collector);
    }

    @Test
    void testSimpleMultiStageLoop() {
        // Build the WayangPlan.
        final List<Integer> collector = new LinkedList<>();
        WayangPlan wayangPlan = WayangPlans.simpleLoop(3, collector, 0, 1, 2);

        wayangPlan.collectTopLevelOperatorByName("source").addTargetPlatform(Spark.platform());
        wayangPlan.collectTopLevelOperatorByName("convergenceSource").addTargetPlatform(Spark.platform());
        wayangPlan.collectTopLevelOperatorByName("loop").addTargetPlatform(Java.platform());
        wayangPlan.collectTopLevelOperatorByName("step").addTargetPlatform(Spark.platform());
        wayangPlan.collectTopLevelOperatorByName("counter").addTargetPlatform(Java.platform());
        wayangPlan.collectTopLevelOperatorByName("sink").addTargetPlatform(Spark.platform());

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);

        final HashSet<Integer> expected = new HashSet<>(WayangArrays.asList(WayangArrays.range(0, 24)));
        assertEquals(expected, WayangCollections.asSet(collector));
    }

    @Test
    void testSimpleSample() {
        // Build the WayangPlan.
        final List<Integer> collector = new LinkedList<>();
        WayangPlan wayangPlan = WayangPlans.simpleSample(3, collector, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        // Instantiate Wayang and activate the Java backend.
        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin());

        wayangContext.execute(wayangPlan);
        System.out.println(collector);
    }

    @Test
    void testCurrentIterationNumber() {
        WayangContext wayangContext = new WayangContext().with(Java.basicPlugin()).with(Spark.basicPlugin());
        final Collection<Integer> result = WayangPlans.loopWithIterationNumber(wayangContext, 15, 5, -1, 1, 5);
        int expectedOffset = 10;
        assertEquals(
                WayangCollections.asSet(-1 + expectedOffset, 1 + expectedOffset, 5 + expectedOffset),
                WayangCollections.asSet(result)
        );
    }

    @Test
    void testCurrentIterationNumberWithTooFewExpectedIterations() {
        WayangContext wayangContext = new WayangContext().with(Java.basicPlugin()).with(Spark.basicPlugin());
        final Collection<Integer> result = WayangPlans.loopWithIterationNumber(wayangContext, 15, 2, -1, 1, 5);
        int expectedOffset = 10;
        assertEquals(
                WayangCollections.asSet(-1 + expectedOffset, 1 + expectedOffset, 5 + expectedOffset),
                WayangCollections.asSet(result)
        );
    }

    @Test
    void testGroupByOperator() {
        final CollectionSource<String> source = new CollectionSource<>(
                Arrays.asList("a", "b", "a", "ab", "aa", "bb"),
                String.class
        );
        final MaterializedGroupByOperator<String, Integer> materializedGroupByOperator =
                new MaterializedGroupByOperator<>(String::length, String.class, Integer.class);
        final MapOperator<Iterable<String>, Integer> mapOperator = new MapOperator<>(
                new TransformationDescriptor<>(
                        strings -> (int) StreamSupport.stream(strings.spliterator(), false).count(),
                        DataUnitType.createBasicUnchecked(Iterable.class),
                        DataUnitType.createBasic(Integer.class)
                ),
                DataSetType.createGrouped(String.class),
                DataSetType.createDefault(Integer.class)
        );
        final Collection<Integer> collector = new LinkedList<>();
        final LocalCallbackSink<Integer> sink = LocalCallbackSink.createCollectingSink(collector, Integer.class);

        source.connectTo(0, materializedGroupByOperator, 0);
        materializedGroupByOperator.connectTo(0, mapOperator, 0);
        mapOperator.connectTo(0, sink, 0);

        WayangContext wayangContext = new WayangContext(configuration)
                .with(Spark.basicPlugin())
                .with(Java.basicPlugin());

        wayangContext.execute(new WayangPlan(sink));
        System.out.println(collector);
    }

    @Test
    void testSqlite3Scenario1() {
        Collection<Record> collector = new ArrayList<>();
        final WayangPlan wayangPlan = WayangPlans.sqlite3Scenario1(collector);

        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin())
                .with(Sqlite3.plugin());

        wayangContext.execute("SQLite3 scenario 1", wayangPlan);

        assertEquals(WayangPlans.getSqlite3Customers(), collector);
    }

    @Test
    void testSqlite3Scenario2() {
        Collection<Record> collector = new ArrayList<>();
        final WayangPlan wayangPlan = WayangPlans.sqlite3Scenario2(collector);

        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin())
                .with(Sqlite3.plugin());

        wayangContext.execute("SQLite3 scenario 2", wayangPlan);

        final List<Record> expected = WayangPlans.getSqlite3Customers().stream()
                .filter(r -> (Integer) r.getField(1) >= 18)
                .collect(Collectors.toList());
        assertEquals(expected, collector);
    }

    @Test
    void testSqlite3Scenario3() {
        Collection<Record> collector = new ArrayList<>();
        final WayangPlan wayangPlan = WayangPlans.sqlite3Scenario3(collector);

        WayangContext wayangContext = new WayangContext(configuration)
                .with(Java.basicPlugin())
                .with(Spark.basicPlugin())
                .with(Sqlite3.plugin());

        wayangContext.execute("SQLite3 scenario 3", wayangPlan);

        final List<Record> expected = WayangPlans.getSqlite3Customers().stream()
                .filter(r -> (Integer) r.getField(1) >= 18)
                .map(r -> new Record(r.getField(0)))
                .collect(Collectors.toList());
        assertEquals(expected, collector);
    }
}

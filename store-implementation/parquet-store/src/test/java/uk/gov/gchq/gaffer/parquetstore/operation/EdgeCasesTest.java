/*
 * Copyright 2017. Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.parquetstore.operation;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Level;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.gov.gchq.gaffer.commonutil.StreamUtil;
import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterator;
import uk.gov.gchq.gaffer.data.element.Edge;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.data.element.Entity;
import uk.gov.gchq.gaffer.graph.Graph;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.operation.data.EntitySeed;
import uk.gov.gchq.gaffer.operation.impl.add.AddElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;
import uk.gov.gchq.gaffer.parquetstore.ParquetStoreProperties;
import uk.gov.gchq.gaffer.parquetstore.testutils.DataGen;
import uk.gov.gchq.gaffer.parquetstore.testutils.TestUtils;
import uk.gov.gchq.gaffer.store.StoreException;
import uk.gov.gchq.gaffer.store.StoreProperties;
import uk.gov.gchq.gaffer.store.schema.Schema;
import uk.gov.gchq.gaffer.types.FreqMap;
import uk.gov.gchq.gaffer.user.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class EdgeCasesTest {
    private static User USER = new User();

    @BeforeClass
    public static void setup() {
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.WARN);
    }

    @AfterClass
    public static void cleanUp() throws IOException {
        try (final FileSystem fs = FileSystem.get(new Configuration())) {
            deleteFolder("parquet_data", fs);
        }
    }

    private static void deleteFolder(final String path, final FileSystem fs) throws IOException {
        Path dataDir = new Path(path);
        if (fs.exists(dataDir)) {
            fs.delete(dataDir, true);
            while (fs.listStatus(dataDir.getParent()).length == 0) {
                dataDir = dataDir.getParent();
                fs.delete(dataDir, true);
            }
        }
    }

    private static ParquetStoreProperties getParquetStoreProperties() {
        return (ParquetStoreProperties) StoreProperties.loadStoreProperties(
                StreamUtil.storeProps(EdgeCasesTest.class));
    }

    @Test
    public void addElementsToExistingFolderTest() throws StoreException, OperationException, IOException {
        final Schema gafferSchema = Schema.fromJson(
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/dataSchema.json"),
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/dataTypes.json"),
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/storeSchema.json"),
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/storeTypes.json"));
        final ParquetStoreProperties parquetStoreProperties = getParquetStoreProperties();
        Graph graph = new Graph.Builder()
                .addSchemas(gafferSchema)
                .storeProperties(parquetStoreProperties)
                .graphId("test")
                .build();
        final FreqMap f2 = new FreqMap();
        f2.upsert("A", 2L);
        f2.upsert("B", 2L);
        final ArrayList<Element> elements = new ArrayList<>(2);
        elements.add(DataGen.getEntity("BasicEntity", "vertex", (byte) 'a', 0.2, 3f, TestUtils.TREESET1, 5L, (short) 6,
                TestUtils.DATE , TestUtils.FREQMAP1, 1));
        final Entity expected = DataGen.getEntity("BasicEntity", "vertex", (byte) 'a', 0.4, 6f, TestUtils.TREESET1, 10L, (short) 12,
                TestUtils.DATE, f2, 2);
        graph.execute(new AddElements.Builder().input(elements).build(), USER);
        graph.execute(new AddElements.Builder().input(elements).build(), USER);
        CloseableIterator<? extends Element> results = graph.execute(new GetAllElements.Builder().build(), USER).iterator();
        assertTrue(results.hasNext());
        assertEquals(expected, results.next());
        assertFalse(results.hasNext());
    }

    @Test
    public void readElementsWithZeroElementFiles() throws IOException, OperationException, StoreException {
        try {
            final List<Element> elements = new ArrayList<>(2);
            elements.add(DataGen.getEntity("BasicEntity", "vert1", null, null, null, null, null, null, null, null, 1));
            elements.add(DataGen.getEntity("BasicEntity", "vert2", null, null, null, null, null, null, null, null, 1));

            final Schema gafferSchema = Schema.fromJson(
                    EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/dataSchema.json"),
                    EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/dataTypes.json"),
                    EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/storeSchema.json"),
                    EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/storeTypes.json"));
            ParquetStoreProperties parquetStoreProperties = getParquetStoreProperties();
            parquetStoreProperties.setDataDir("readElementsWithZeroElementFiles");
            parquetStoreProperties.setAddElementsOutputFilesPerGroup(3);
            final Graph graph = new Graph.Builder()
                    .addSchema(gafferSchema)
                    .storeProperties(parquetStoreProperties)
                    .graphId("test")
                    .build();
            graph.execute(new AddElements.Builder().input(elements).build(), USER);
            final List<Element> retrievedElements = new ArrayList<>();
            final CloseableIterator<? extends Element> iter = graph.execute(new GetAllElements(), USER).iterator();
            assertTrue(iter.hasNext());
            while (iter.hasNext()) {
                retrievedElements.add(iter.next());
            }
            assertThat(elements, containsInAnyOrder(retrievedElements.toArray()));
        } finally {
            try (final FileSystem fs = FileSystem.get(new Configuration())) {
                deleteFolder("readElementsWithZeroElementFiles", fs);
            }
        }
    }

    @Test
    public void indexOutOfRangeTest() throws IOException, StoreException, OperationException {
        final Schema gafferSchema = Schema.fromJson(
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/dataSchema.json"),
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/dataTypes.json"),
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/storeSchema.json"),
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/storeTypes.json"));
        final ParquetStoreProperties parquetStoreProperties = getParquetStoreProperties();
        parquetStoreProperties.setAddElementsOutputFilesPerGroup(1);
        final Graph graph = new Graph.Builder()
                .addSchemas(gafferSchema)
                .storeProperties(parquetStoreProperties)
                .graphId("test")
                .build();

        final ArrayList<Element> elements = new ArrayList<>(2);
        final Edge A2B = new Edge("BasicEdge", "A", "B", false);
        A2B.putProperty("count", 1);
        final Edge B2A = new Edge("BasicEdge", "B", "A", false);
        B2A.putProperty("count", 1);
        elements.add(A2B);
        elements.add(B2A);
        graph.execute(new AddElements.Builder().input(elements).build(), USER);

        final List<EntitySeed> entitySeeds = new ArrayList<>(1);
        entitySeeds.add(new EntitySeed("0"));
        Iterable<? extends Element> results = graph.execute(new GetElements.Builder().input(entitySeeds).build(), USER);
        Iterator<? extends Element> iter = results.iterator();
        assertFalse(iter.hasNext());

        entitySeeds.clear();
        entitySeeds.add(new EntitySeed("a"));
        results = graph.execute(new GetElements.Builder().input(entitySeeds).build(), USER);
        iter = results.iterator();
        assertFalse(iter.hasNext());
    }

    @Test
    public void deduplicateEdgeWhenSrcAndDstAreEqual() throws OperationException {
        final Schema gafferSchema = Schema.fromJson(
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/dataSchema.json"),
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/dataTypes.json"),
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/storeSchema.json"),
                EdgeCasesTest.class.getResourceAsStream("/schemaUsingStringVertexType/storeTypes.json"));
        ParquetStoreProperties parquetStoreProperties = getParquetStoreProperties();
        parquetStoreProperties.setAddElementsOutputFilesPerGroup(1);
        Graph graph = new Graph.Builder()
                .addSchemas(gafferSchema)
                .storeProperties(parquetStoreProperties)
                .graphId("test")
                .build();

        final ArrayList<Element> elements = new ArrayList<>(2);
        final Edge A2A = new Edge("BasicEdge", "A", "A", false);
        A2A.putProperty("count", 1);
        elements.add(A2A);
        graph.execute(new AddElements.Builder().input(elements).build(), USER);

        final List<EntitySeed> entitySeeds = new ArrayList<>(1);
        entitySeeds.add(new EntitySeed("A"));
        Iterable<? extends Element> results = graph.execute(new GetElements.Builder().input(entitySeeds).build(), USER);
        Iterator<? extends Element> iter = results.iterator();
        assertTrue(iter.hasNext());
        assertEquals(A2A, iter.next());
        assertFalse(iter.hasNext());

        results = graph.execute(new GetAllElements.Builder().build(), USER);
        iter = results.iterator();
        assertTrue(iter.hasNext());
        assertEquals(A2A, iter.next());
        assertFalse(iter.hasNext());
    }
}

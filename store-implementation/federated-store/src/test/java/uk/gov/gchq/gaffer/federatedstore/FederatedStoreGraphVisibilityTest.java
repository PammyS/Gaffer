/*
 * Copyright 2017 Crown Copyright
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

package uk.gov.gchq.gaffer.federatedstore;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import uk.gov.gchq.gaffer.accumulostore.AccumuloProperties;
import uk.gov.gchq.gaffer.accumulostore.SingleUseMockAccumuloStore;
import uk.gov.gchq.gaffer.federatedstore.operation.AddGraph;
import uk.gov.gchq.gaffer.federatedstore.operation.GetAllGraphIds;
import uk.gov.gchq.gaffer.graph.Graph;
import uk.gov.gchq.gaffer.graph.Graph.Builder;
import uk.gov.gchq.gaffer.graph.GraphConfig;
import uk.gov.gchq.gaffer.store.StoreProperties;
import uk.gov.gchq.gaffer.store.library.HashMapGraphLibrary;
import uk.gov.gchq.gaffer.store.schema.Schema;
import uk.gov.gchq.gaffer.store.schema.SchemaEntityDefinition;
import uk.gov.gchq.gaffer.user.User;

import java.util.HashSet;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreUser.authUser;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreUser.blankUser;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreUser.testUser;

public class FederatedStoreGraphVisibilityTest {

    private static User addingUser;
    private static User nonAddingUser;
    private static User authNonAddingUser;
    private Graph fedGraph;
    private StoreProperties fedProperties;

    @Before
    public void setUp() throws Exception {
        HashMapGraphLibrary.clear();
        fedProperties = new FederatedStoreProperties();

        final HashMapGraphLibrary library = new HashMapGraphLibrary();

        final Schema aSchema = new Schema.Builder()
                .entity("e1", new SchemaEntityDefinition.Builder()
                        .vertex("string")
                        .build())
                .type("string", String.class)
                .build();

        final AccumuloProperties accProp = new AccumuloProperties();
        accProp.setStoreClass(SingleUseMockAccumuloStore.class.getName());
        accProp.setStorePropertiesClass(AccumuloProperties.class);

        library.add("a", aSchema, accProp);

        fedGraph = new Builder()
                .config(new GraphConfig.Builder()
                        .graphId("testFedGraph")
                        .library(library)
                        .build())
                .addStoreProperties(fedProperties)
                .build();

        addingUser = testUser();
        nonAddingUser = blankUser();
        authNonAddingUser = authUser();
    }

    @Test
    public void shouldNotShowHiddenGraphId() throws Exception {
        fedGraph.execute(
                new AddGraph.Builder()
                        .graphId("g1")
                        .parentPropertiesId("a")
                        .build(),
                addingUser);

        fedGraph.execute(
                new AddGraph.Builder()
                        .graphId("g2")
                        .parentPropertiesId("a")
                        .graphAuths("auth1")
                        .build(),
                addingUser);


        Iterable<? extends String> graphIds = fedGraph.execute(
                new GetAllGraphIds(),
                nonAddingUser);


        final HashSet<Object> sets = Sets.newHashSet();
        Iterator<? extends String> iterator = graphIds.iterator();
        while (iterator.hasNext()) {
            sets.add(iterator.next());
        }

        assertNotNull("Returned iterator should not be null, it should be empty.", graphIds);
        assertEquals("Showing hidden graphId", 0, sets.size());


        graphIds = fedGraph.execute(
                new GetAllGraphIds(),
                authNonAddingUser);
        iterator = graphIds.iterator();

        sets.clear();
        while (iterator.hasNext()) {
            sets.add(iterator.next());
        }

        assertNotNull("Returned iterator should not be null, it should be empty.", graphIds);
        assertEquals("Not Showing graphId with correct auth", 1, sets.size());
        assertTrue(sets.contains("g2"));


        graphIds = fedGraph.execute(
                new GetAllGraphIds(),
                addingUser);
        iterator = graphIds.iterator();


        sets.clear();
        while (iterator.hasNext()) {
            sets.add(iterator.next());
        }

        assertNotNull("Returned iterator should not be null, it should be empty.", graphIds);
        assertEquals("Not Showing all graphId for adding user", 2, sets.size());
        assertTrue(sets.contains("g1"));
        assertTrue(sets.contains("g2"));
    }

}
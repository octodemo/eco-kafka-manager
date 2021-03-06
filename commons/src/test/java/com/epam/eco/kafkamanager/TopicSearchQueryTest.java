/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.epam.eco.kafkamanager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.epam.eco.kafkamanager.TopicSearchQuery.ReplicationState;
import com.epam.eco.kafkamanager.utils.TestObjectMapperSingleton;

/**
 * @author Andrei_Tytsik
 */
public class TopicSearchQueryTest {

    @Test
    public void testSerializedToJsonAndBack() throws Exception {
        TopicSearchQuery origin = TopicSearchQuery.builder().
                topicName("topicName").
                minPartitionCount(1).
                maxPartitionCount(10).
                minReplicationFactor(2).
                maxReplicationFactor(11).
                replicationStateFully().
                configMap(Collections.singletonMap("key1", "value1")).
                configString("key2:value2;key3:value3").
                description("description").
                build();

        ObjectMapper mapper = TestObjectMapperSingleton.getObjectMapper();

        String json = mapper.writeValueAsString(origin);
        Assert.assertNotNull(json);

        TopicSearchQuery deserialized = mapper.readValue(
                json,
                TopicSearchQuery.class);
        Assert.assertNotNull(deserialized);
        Assert.assertEquals(origin, deserialized);
    }

    @Test
    public void testDeserializedFromJson1() throws Exception {
        Map<String, Object> json = new HashMap<>();
        json.put("topicName", "topic1");
        json.put("minPartitionCount", 1);
        json.put("maxPartitionCount", 10);
        json.put("minReplicationFactor", 1);
        json.put("maxReplicationFactor", 11);
        json.put("replicationState", ReplicationState.UNDER_REPLICATED);
        json.put("configMap", Collections.singletonMap("key1", "value1"));
        json.put("configString", "key2:value2;key3:value3");
        json.put("description", "description");

        TopicSearchQuery query = TopicSearchQuery.fromJson(json);
        Assert.assertNotNull(query);
        Assert.assertEquals("topic1", query.getTopicName());
        Assert.assertEquals(Integer.valueOf(1), query.getMinPartitionCount());
        Assert.assertEquals(Integer.valueOf(10), query.getMaxPartitionCount());
        Assert.assertEquals(Integer.valueOf(1), query.getMinReplicationFactor());
        Assert.assertEquals(Integer.valueOf(11), query.getMaxReplicationFactor());
        Assert.assertEquals(ReplicationState.UNDER_REPLICATED, query.getReplicationState());
        Map<String, Object> config = new HashMap<>();
        config.put("key1", "value1");
        Assert.assertEquals(config, query.getConfigMap());
        Assert.assertEquals("key2:value2;key3:value3", query.getConfigString());
        Assert.assertEquals("description", query.getDescription());
    }

    @Test
    public void testDeserializedFromJson2() throws Exception {
        String json =
                "{" +
                "\"topicName\": \"topic1\"" +
                ", \"minPartitionCount\": 1" +
                ", \"maxPartitionCount\": 10" +
                ", \"minReplicationFactor\": 1" +
                ", \"maxReplicationFactor\": 11" +
                ", \"replicationState\": \"UNDER_REPLICATED\"" +
                ", \"configMap\": {\"key1\":\"value1\"}" +
                ", \"configString\": \"key2:value2;key3:value3\"" +
                ", \"description\": \"description\"" +
                "}";

        TopicSearchQuery query = TopicSearchQuery.fromJson(json);
        Assert.assertNotNull(query);
        Assert.assertEquals("topic1", query.getTopicName());
        Assert.assertEquals(Integer.valueOf(1), query.getMinPartitionCount());
        Assert.assertEquals(Integer.valueOf(10), query.getMaxPartitionCount());
        Assert.assertEquals(Integer.valueOf(1), query.getMinReplicationFactor());
        Assert.assertEquals(Integer.valueOf(11), query.getMaxReplicationFactor());
        Assert.assertEquals(ReplicationState.UNDER_REPLICATED, query.getReplicationState());
        Map<String, Object> config = new HashMap<>();
        config.put("key1", "value1");
        Assert.assertEquals(config, query.getConfigMap());
        Assert.assertEquals("key2:value2;key3:value3", query.getConfigString());
        Assert.assertEquals("description", query.getDescription());
    }

    @Test
    public void testConfigStringParsed() throws Exception {
        Map<String, String> config = TopicSearchQuery.parseConfigString(":");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey(null));
        Assert.assertEquals(null, config.get(null));

        config = TopicSearchQuery.parseConfigString(" :");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey(null));
        Assert.assertEquals(null, config.get(null));

        config = TopicSearchQuery.parseConfigString(" : ");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey(null));
        Assert.assertEquals(null, config.get(null));

        config = TopicSearchQuery.parseConfigString(" : ;   :  ");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey(null));
        Assert.assertEquals(null, config.get(null));

        config = TopicSearchQuery.parseConfigString(" : ;  x :  ");
        Assert.assertNotNull(config);
        Assert.assertEquals(2, config.size());
        Assert.assertTrue(config.containsKey(null));
        Assert.assertEquals(null, config.get(null));
        Assert.assertTrue(config.containsKey("x"));
        Assert.assertEquals(null, config.get("x"));

        config = TopicSearchQuery.parseConfigString("x:");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey("x"));
        Assert.assertEquals(null, config.get("x"));

        config = TopicSearchQuery.parseConfigString(" x:");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey("x"));
        Assert.assertEquals(null, config.get("x"));

        config = TopicSearchQuery.parseConfigString(" x :");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey("x"));
        Assert.assertEquals(null, config.get("x"));

        config = TopicSearchQuery.parseConfigString(":y");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey(null));
        Assert.assertEquals("y", config.get(null));

        config = TopicSearchQuery.parseConfigString(": y");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey(null));
        Assert.assertEquals("y", config.get(null));

        config = TopicSearchQuery.parseConfigString(": y ");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey(null));
        Assert.assertEquals("y", config.get(null));

        config = TopicSearchQuery.parseConfigString(" x    :  y    ");
        Assert.assertNotNull(config);
        Assert.assertEquals(1, config.size());
        Assert.assertTrue(config.containsKey("x"));
        Assert.assertEquals("y", config.get("x"));

        config = TopicSearchQuery.parseConfigString(" x    :  y    ;     a:b;   c:   ");
        Assert.assertNotNull(config);
        Assert.assertEquals(3, config.size());
        Assert.assertTrue(config.containsKey("x"));
        Assert.assertEquals("y", config.get("x"));
        Assert.assertTrue(config.containsKey("a"));
        Assert.assertEquals("b", config.get("a"));
        Assert.assertTrue(config.containsKey("c"));
        Assert.assertEquals(null, config.get("c"));
    }

}

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
package com.epam.eco.kafkamanager.core.consumer.repo.kafka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.Validate;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.epam.eco.commons.kafka.ScalaConversions;
import com.epam.eco.kafkamanager.ConsumerGroupInfo;
import com.epam.eco.kafkamanager.ConsumerGroupInfo.StorageType;
import com.epam.eco.kafkamanager.ConsumerGroupMetadataKey;
import com.epam.eco.kafkamanager.ConsumerGroupRepo;
import com.epam.eco.kafkamanager.ConsumerGroupSearchQuery;
import com.epam.eco.kafkamanager.ConsumerInfo;
import com.epam.eco.kafkamanager.EntityType;
import com.epam.eco.kafkamanager.Metadata;
import com.epam.eco.kafkamanager.MetadataKey;
import com.epam.eco.kafkamanager.MetadataRepo;
import com.epam.eco.kafkamanager.MetadataUpdateListener;
import com.epam.eco.kafkamanager.NotFoundException;
import com.epam.eco.kafkamanager.OffsetAndMetadataInfo;
import com.epam.eco.kafkamanager.core.autoconfigure.KafkaManagerProperties;
import com.epam.eco.kafkamanager.core.spring.AsyncStartingBean;
import com.epam.eco.kafkamanager.repo.AbstractKeyValueRepo;
import com.epam.eco.kafkamanager.repo.CachedRepo;

import kafka.common.OffsetAndMetadata;
import kafka.coordinator.group.GroupMetadata;
import kafka.coordinator.group.MemberMetadata;
import kafka.coordinator.group.OffsetKey;

/**
 * @author Andrei_Tytsik
 */
public class KafkaConsumerGroupRepo extends AbstractKeyValueRepo<String, ConsumerGroupInfo, ConsumerGroupSearchQuery> implements ConsumerGroupRepo, CachedRepo<String>, KafkaConsumerGroupCache.CacheListener, MetadataUpdateListener, AsyncStartingBean {

    private final static Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerGroupRepo.class);

    @Autowired
    private KafkaManagerProperties properties;
    @Autowired
    private MetadataRepo metadataRepo;

    private KafkaConsumerGroupCache groupCache;

    private final Map<String, ConsumerGroupInfo> groupInfoCache = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        initGroupCache();
        subscribeOnMetadataUpdates();

        LOGGER.info("Initialized");
    }

    @Override
    public void startAsync() throws Exception {
        startGroupCache();

        LOGGER.info("Started");
    }

    @PreDestroy
    private void destroy() {
        destroyGroupCache();

        LOGGER.info("Destroyed");
    }

    private void initGroupCache() {
        groupCache = new KafkaConsumerGroupCache(
                properties.getBootstrapServers(),
                properties.getClientConfig(),
                properties.getConsumerStoreBootstrapTimeoutInMs(),
                properties.getConsumerStoreBootstrapDataFreshness(),
                this);
    }

    private void startGroupCache() throws Exception {
        groupCache.start();
    }

    private void destroyGroupCache() {
        groupCache.close();
    }

    private void subscribeOnMetadataUpdates() {
        metadataRepo.registerUpdateListener(this);
    }

    @Override
    public int size() {
        return groupCache.size();
    }

    @Override
    public boolean contains(String groupName) {
        Validate.notBlank(groupName, "Group name can't be blank");

        return groupCache.contains(groupName);
    }

    @Override
    public ConsumerGroupInfo get(String groupName) {
        Validate.notBlank(groupName, "Group name can't be blank");

        ConsumerGroupInfo groupInfo = getGroupFromInfoCacheOrCreate(groupName);
        if (groupInfo == null) {
            throw new NotFoundException(String.format("Group not found by name '%s'", groupName));
        }

        return groupInfo;
    }

    @Override
    public List<ConsumerGroupInfo> values() {
        List<ConsumerGroupInfo> groupInfos = new ArrayList<>();
        groupCache.listGroupNames().forEach(groupName -> {
            ConsumerGroupInfo groupInfo = getGroupFromInfoCacheOrCreate(groupName);
            if (groupInfo != null) {
                groupInfos.add(groupInfo);
            }
        });
        Collections.sort(groupInfos);
        return groupInfos;
    }

    @Override
    public List<ConsumerGroupInfo> values(List<String> groupNames) {
        Validate.noNullElements(
                groupNames, "Group name collection can't be null or contain null elements");

        List<ConsumerGroupInfo> groupInfos = new ArrayList<>();
        groupNames.forEach(groupName -> {
            ConsumerGroupInfo groupInfo = getGroupFromInfoCacheOrCreate(groupName);
            if (groupInfo != null) {
                groupInfos.add(groupInfo);
            }
        });
        Collections.sort(groupInfos);
        return groupInfos;
    }

    @Override
    public List<String> keys() {
        return groupCache.listGroupNames().stream().
                sorted().
                collect(Collectors.toList());
    }

    @Override
    public List<ConsumerGroupInfo> groupsForTopic(String topicName) {
        Validate.notBlank(topicName, "Topic name can't be blank");

        return values(
                groupCache.listGroupNamesOfTopic(topicName).stream().
                    sorted().
                    collect(Collectors.toList()));
    }

    @Override
    public ConsumerGroupInfo unassignGroupFromTopic(String groupName, String topicName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void evict(String groupName) {
        removeGroupFromInfoCache(groupName);
    }

    @Override
    public void onGroupMetadataUpdated(KafkaGroupMetadata groupMetadata) {
        Validate.notNull(groupMetadata, "Group metadata can't be null");

        removeGroupFromInfoCache(groupMetadata.getName());
    }

    @Override
    public void onGroupMetadataRemoved(String groupName) {
        removeGroupFromInfoCache(groupName);
    }

    @Override
    public void onMetadataUpdated(MetadataKey key, Metadata metadata) {
        Validate.notNull(key, "Metadata key is null");
        Validate.notNull(metadata, "Metadata is null");

        if (key.getEntityType() != EntityType.CONSUMER_GROUP) {
            return;
        }

        removeGroupFromInfoCache(((ConsumerGroupMetadataKey)key).getGroupName());
    }

    @Override
    public void onMetadataRemoved(MetadataKey key) {
        Validate.notNull(key, "Metadata key is null");

        if (key.getEntityType() != EntityType.CONSUMER_GROUP) {
            return;
        }

        removeGroupFromInfoCache(((ConsumerGroupMetadataKey)key).getGroupName());
    }

    private void removeGroupFromInfoCache(String groupName) {
        Validate.notBlank(groupName, "Group name can't be null");

        groupInfoCache.remove(groupName);
    }

    private ConsumerGroupInfo getGroupFromInfoCacheOrCreate(String groupName) {
        return groupInfoCache.computeIfAbsent(
                groupName,
                key -> {
                    KafkaGroupMetadata groupMetadata = groupCache.getGroupMetadata(groupName);
                    return groupMetadata != null ? toConsumerGroupInfo(groupMetadata) : null;
                });
    }

    private ConsumerGroupInfo toConsumerGroupInfo(KafkaGroupMetadata metadata) {
        String groupName = metadata.getName();
        List<ConsumerInfo> consumerInfos = toConsumerInfos(metadata.getGroupMetadata());
        Map<TopicPartition, OffsetAndMetadataInfo> offsetAndMetadataInfos =
                toOffsetAndMetadataInfos(metadata.getOffsetsAndMetadata());
        return ConsumerGroupInfo.builder().
                name(groupName).
                members(consumerInfos).
                offsetsAndMetadata(offsetAndMetadataInfos).
                offsetTimeSeries(groupCache.getOffsetTimeSeries(groupName)).
                storageType(StorageType.KAFKA).
                metadata(metadataRepo.get(ConsumerGroupMetadataKey.with(groupName))).
                build();
    }

    private static List<ConsumerInfo> toConsumerInfos(GroupMetadata groupMetadata) {
        if (groupMetadata == null || groupMetadata.allMembers().isEmpty()) {
            return Collections.emptyList();
        }

        List<MemberMetadata> memberMetadatas = ScalaConversions.
                asJavaList(groupMetadata.allMemberMetadata());
        return memberMetadatas.stream().
                map(metadata -> ConsumerInfo.builder().
                        groupName(metadata.groupId()).
                        clientId(metadata.clientId()).
                        memberId(metadata.memberId()).
                        clientHost(metadata.clientHost()).
                        protocolType(metadata.protocolType()).
                        rebalanceTimeoutMs(metadata.rebalanceTimeoutMs()).
                        sessionTimeoutMs(metadata.sessionTimeoutMs()).
                        latestHeartbeatDate(metadata.latestHeartbeat()).
                        build()).
                sorted().
                collect(Collectors.toList());
    }

    private static Map<TopicPartition, OffsetAndMetadataInfo> toOffsetAndMetadataInfos(
            Map<OffsetKey, OffsetAndMetadata> offsetsAndMetadata) {
        if (offsetsAndMetadata.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<TopicPartition, OffsetAndMetadataInfo> offsetAndMetadataInfos = new HashMap<>();
        offsetsAndMetadata.keySet().forEach(offsetKey -> {
            OffsetAndMetadata offsetAndMetadata = offsetsAndMetadata.get(offsetKey);
            TopicPartition topicPartition = offsetKey.key().topicPartition();
            OffsetAndMetadataInfo offsetAndMetadataInfo = OffsetAndMetadataInfo.builder().
                    topicPartition(topicPartition).
                    offset(offsetAndMetadata.offset()).
                    metadata(offsetAndMetadata.metadata()).
                    commitDate(offsetAndMetadata.commitTimestamp()).
                    expireDate(
                            offsetAndMetadata.expireTimestamp().isDefined() ?
                            (Long)offsetAndMetadata.expireTimestamp().get() :
                            -1L).
                    build();
            offsetAndMetadataInfos.put(topicPartition, offsetAndMetadataInfo);
        });
        return offsetAndMetadataInfos;
    }

}

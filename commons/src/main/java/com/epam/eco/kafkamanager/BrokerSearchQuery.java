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

import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.epam.eco.kafkamanager.utils.MapperUtils;

/**
 * @author Andrei_Tytsik
 */
public class BrokerSearchQuery implements SearchQuery<BrokerInfo> {

    private final Integer brokerId;
    private final String rack;
    private final String description;

    public BrokerSearchQuery(
            @JsonProperty("brokerId") Integer brokerId,
            @JsonProperty("rack") String rack,
            @JsonProperty("description") String description) {
        this.brokerId = brokerId;
        this.rack = rack;
        this.description = description;
    }

    public Integer getBrokerId() {
        return brokerId;
    }
    public String getRack() {
        return rack;
    }
    public String getDescription() {
        return description;
    }

    @Override
    public boolean matches(BrokerInfo obj) {
        Validate.notNull(obj, "Broker Info is null");

        return
                (brokerId == null || Objects.equals(obj.getId(), brokerId)) &&
                (StringUtils.isBlank(rack) || StringUtils.containsIgnoreCase(obj.getRack(), rack)) &&
                (
                        StringUtils.isBlank(description) ||
                        StringUtils.containsIgnoreCase(
                                obj.getMetadata().map(Metadata::getDescription).orElse(null), description));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BrokerSearchQuery that = (BrokerSearchQuery) obj;
        return
                Objects.equals(this.brokerId, that.brokerId) &&
                Objects.equals(this.rack, that.rack) &&
                Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(brokerId, rack, description);
    }

    @Override
    public String toString() {
        return
                "{brokerId: " + brokerId +
                ", rack: " + rack +
                ", description: " + description +
                "}";
    }

    public Builder toBuilder() {
        return builder(this);
    }

    public static Builder builder() {
        return builder(null);
    }

    public static Builder builder(BrokerSearchQuery origin) {
        return new Builder(origin);
    }

    public static BrokerSearchQuery fromJson(Map<String, ?> map) {
        Validate.notNull(map, "JSON map is null");

        return MapperUtils.convert(map, BrokerSearchQuery.class);
    }

    public static BrokerSearchQuery fromJson(String json) {
        Validate.notNull(json, "JSON is null");

        return MapperUtils.jsonToBean(json, BrokerSearchQuery.class);
    }

    public static class Builder {

        private Integer brokerId;
        private String rack;
        private String description;

        private Builder(BrokerSearchQuery origin) {
            if (origin == null) {
                return;
            }

            this.brokerId = origin.brokerId;
            this.rack = origin.rack;
            this.description = origin.description;
        }

        public Builder brokerId(Integer brokerId) {
            this.brokerId = brokerId;
            return this;
        }

        public Builder rack(String rack) {
            this.rack = rack;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public BrokerSearchQuery build() {
            return new BrokerSearchQuery(brokerId, rack, description);
        }

    }

}

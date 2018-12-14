/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider;
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials;
import com.oracle.bmc.core.model.CreateInstancePoolPlacementConfigurationDetails;
import com.oracle.bmc.core.model.InstancePool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("rawtypes")
@JsonIgnoreProperties(ignoreUnknown = true)
public class OracleServerGroup {

  String name;
  String region;
  String zone;
  Set<String> zones = new HashSet<>();
  Set<OracleInstance> instances  = new HashSet<>();
  Map<String, Object> launchConfig = new HashMap<>();
  Set<String> securityGroups  = new HashSet<>();
  Map buildInfo;
  Boolean disabled = false;
  Integer targetSize;
  String loadBalancerId;
  String backendSetName;
  String cloudProvider;
  String instancePoolId;
  String instanceConfigurationId;
  List<CreateInstancePoolPlacementConfigurationDetails> placements;

  @JsonIgnore
  OracleNamedAccountCredentials credentials;
  @JsonIgnore
  InstancePool instancePool;

  public OracleServerGroup() {}

  public OracleServerGroup(
    String serverGroupName,
    String region,
    String availabilityDomain,
    Map<String, Object> launchConfig,
    Integer targetSize,
    OracleNamedAccountCredentials credentials,
    String loadBalancerId,
    String backendSetName,
    List<CreateInstancePoolPlacementConfigurationDetails> placements) {
    this.name = serverGroupName;
    this.region = region;
    this.zone = availabilityDomain;
    this.launchConfig = launchConfig;
    this.targetSize = targetSize;
    this.credentials = credentials;
    this.loadBalancerId = loadBalancerId;
    this.backendSetName = backendSetName;
    this.placements = placements;
  }

  @JsonIgnore
  View getView() {
    return new View();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  class View implements ServerGroup {

    final String type = OracleCloudProvider.ID;
    final String cloudProvider = OracleCloudProvider.ID;
    String name = OracleServerGroup.this.name;
    String region = OracleServerGroup.this.region;
    String zone = OracleServerGroup.this.zone;
    Set<String> zones = OracleServerGroup.this.zones;
    Set<OracleInstance> instances = OracleServerGroup.this.instances;
    Map<String, Object> launchConfig = OracleServerGroup.this.launchConfig;
    Set<String> securityGroups = OracleServerGroup.this.securityGroups;
    Map buildInfo = OracleServerGroup.this.buildInfo;
    Boolean disabled = OracleServerGroup.this.disabled;
    ServerGroup.Capacity capacity =  ServerGroup.Capacity.builder()
      .desired(OracleServerGroup.this.targetSize)
      .min(OracleServerGroup.this.targetSize)
      .max(OracleServerGroup.this.targetSize).build();

    @Override
    public String getType() {
      return type;
    }

    @Override
    public String getCloudProvider() {
      return cloudProvider;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getRegion() {
      return region;
    }

    public String getZone() {
      return zone;
    }

    @Override
    public Set<String> getZones() {
      return zones;
    }

    @Override
    public Set<OracleInstance> getInstances() {
      return instances;
    }

    @Override
    public Map<String, Object> getLaunchConfig() {
      return launchConfig;
    }

    @Override
    public Set<String> getSecurityGroups() {
      return securityGroups;
    }

    @Override
    public Boolean isDisabled() { // Because groovy isn't smart enough to generate this method :-(
      return disabled;
    }

    @Override
    public Long getCreatedTime() {
      return (launchConfig != null) ? (Long)launchConfig.get("createdTime") : null;
    }

    @Override
    public  Set<String> getLoadBalancers() {
      Set<String> set = new HashSet<>();
      set.add(OracleServerGroup.this.loadBalancerId);
      return set;
    }

    @Override
    public ServerGroup.Capacity getCapacity() {
      return capacity;
    }

    @Override
    public ServerGroup.ImagesSummary getImagesSummary() {
      return new ServerGroup.ImagesSummary() {
        @Override
        public List<ServerGroup.ImageSummary> getSummaries() {
          return listSummaries();
        }
      };
    }

    Object launchConfig(String name) {
      return (launchConfig != null) ? launchConfig.get(name) : null;
    }

    @SuppressWarnings("unchecked")
    List<ServerGroup.ImageSummary> listSummaries() {
      Map<String, Object> bi = OracleServerGroup.this.buildInfo;
      List<ServerGroup.ImageSummary> summaries = new ArrayList<>();

      ServerGroup.ImageSummary summary = new ServerGroup.ImageSummary() {
        @Override
        public String getServerGroupName() {
          return name;
        }
        @Override
        public String getImageName() { //TODO find the image displayName
          Map<String, Object> instanceTemplate = getImage();
          return (instanceTemplate != null)? (String) instanceTemplate.get("name") : null;
        }
        @Override
        public String getImageId() {
          return (String) launchConfig("imageId");
        }

        @Override
        public Map<String, Object> getBuildInfo() {
          return bi;
        }
        @Override
        public Map<String, Object> getImage() {
          return (Map<String, Object>) launchConfig("instanceTemplate");
        }
      };
      summaries.add(summary);
      return summaries;
    }

    @Override
    public ServerGroup.ImageSummary getImageSummary() {
      ServerGroup.ImagesSummary imagesSummary = getImagesSummary();
      if (imagesSummary != null) {
        List<? extends ServerGroup.ImageSummary> summaries = imagesSummary.getSummaries();
        if (summaries != null && !summaries.isEmpty()) {
          return summaries.get(0);
        } else {
          return null;
        }
      } else {
        return null;
      }
    }

    @Override
    public ServerGroup.InstanceCounts getInstanceCounts() {
      return ServerGroup.InstanceCounts.builder()
        .total(instances.size())
        .up(sizeOf(instances, HealthState.Up))
        .down(sizeOf(instances, HealthState.Down))
        .unknown(sizeOf(instances, HealthState.Unknown))
        .starting(sizeOf(instances, HealthState.Starting))
        .outOfService(sizeOf(instances, HealthState.OutOfService)).build();
    }

    int sizeOf(Set<OracleInstance> instances, HealthState healthState) {
      Long size = instances.stream().filter(it -> it.getHealthState() == healthState).count();
      return size.intValue();
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public Set<String> getZones() {
    return zones;
  }

  public void setZones(Set<String> zones) {
    this.zones = zones;
  }

  public Set<OracleInstance> getInstances() {
    return instances;
  }

  public void setInstances(Set<OracleInstance> instances) {
    this.instances = instances;
  }

  public Map<String, Object> getLaunchConfig() {
    return launchConfig;
  }

  public void setLaunchConfig(Map<String, Object> launchConfig) {
    this.launchConfig = launchConfig;
  }

  public Set<String> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(Set<String> securityGroups) {
    this.securityGroups = securityGroups;
  }

  public Map getBuildInfo() {
    return buildInfo;
  }

  public void setBuildInfo(Map buildInfo) {
    this.buildInfo = buildInfo;
  }

  public Boolean getDisabled() {
    return disabled;
  }

  public void setDisabled(Boolean disabled) {
    this.disabled = disabled;
  }

  public Integer getTargetSize() {
    return targetSize;
  }

  public void setTargetSize(Integer targetSize) {
    this.targetSize = targetSize;
  }

  public String getLoadBalancerId() {
    return loadBalancerId;
  }

  public void setLoadBalancerId(String loadBalancerId) {
    this.loadBalancerId = loadBalancerId;
  }

  public String getBackendSetName() {
    return backendSetName;
  }

  public void setBackendSetName(String backendSetName) {
    this.backendSetName = backendSetName;
  }

  public String getCloudProvider() {
    return cloudProvider;
  }

  public void setCloudProvider(String cloudProvider) {
    this.cloudProvider = cloudProvider;
  }

  public String getInstancePoolId() {
    return instancePoolId;
  }

  public void setInstancePoolId(String instancePoolId) {
    this.instancePoolId = instancePoolId;
  }

  public String getInstanceConfigurationId() {
    return instanceConfigurationId;
  }

  public void setInstanceConfigurationId(String instanceConfigurationId) {
    this.instanceConfigurationId = instanceConfigurationId;
  }

  public List<CreateInstancePoolPlacementConfigurationDetails> getPlacements() {
    return placements;
  }

  public void setPlacements(List<CreateInstancePoolPlacementConfigurationDetails> placements) {
    this.placements = placements;
  }

  public OracleNamedAccountCredentials getCredentials() {
    return credentials;
  }

  public void setCredentials(OracleNamedAccountCredentials credentials) {
    this.credentials = credentials;
  }

  public InstancePool getInstancePool() {
    return instancePool;
  }

  public void setInstancePool(InstancePool instancePool) {
    this.instancePool = instancePool;
  }
}

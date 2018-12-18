/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.service.servergroup;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider;
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller;
import com.netflix.spinnaker.clouddriver.oracle.model.Details;
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance;
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup;
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials;
import com.oracle.bmc.core.ComputeManagementClient;
import com.oracle.bmc.core.model.ComputeInstanceDetails;
import com.oracle.bmc.core.model.CreateInstanceConfigurationDetails;
import com.oracle.bmc.core.model.CreateInstancePoolDetails;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.InstanceConfiguration;
import com.oracle.bmc.core.model.InstanceConfigurationCreateVnicDetails;
import com.oracle.bmc.core.model.InstanceConfigurationInstanceSourceViaImageDetails;
import com.oracle.bmc.core.model.InstanceConfigurationLaunchInstanceDetails;
import com.oracle.bmc.core.model.InstancePool;
import com.oracle.bmc.core.model.InstancePool.LifecycleState;
import com.oracle.bmc.core.model.LaunchInstanceDetails;
import com.oracle.bmc.core.model.UpdateInstancePoolDetails;
import com.oracle.bmc.core.model.UpdateInstancePoolPlacementConfigurationDetails;
import com.oracle.bmc.core.model.Vnic;
import com.oracle.bmc.core.model.VnicAttachment;
import com.oracle.bmc.core.requests.CreateInstanceConfigurationRequest;
import com.oracle.bmc.core.requests.CreateInstancePoolRequest;
import com.oracle.bmc.core.requests.GetInstancePoolRequest;
import com.oracle.bmc.core.requests.GetVnicRequest;
import com.oracle.bmc.core.requests.LaunchInstanceRequest;
import com.oracle.bmc.core.requests.ListInstancePoolInstancesRequest;
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest;
import com.oracle.bmc.core.requests.TerminateInstanceRequest;
import com.oracle.bmc.core.requests.UpdateInstancePoolRequest;
import com.oracle.bmc.core.responses.CreateInstanceConfigurationResponse;
import com.oracle.bmc.core.responses.CreateInstancePoolResponse;
import com.oracle.bmc.core.responses.GetInstancePoolResponse;
import com.oracle.bmc.core.responses.LaunchInstanceResponse;
import com.oracle.bmc.core.responses.ListInstancePoolInstancesResponse;
import com.oracle.bmc.core.responses.ListVnicAttachmentsResponse;
import com.oracle.bmc.core.responses.UpdateInstancePoolResponse;
import com.oracle.bmc.loadbalancer.model.BackendDetails;
import com.oracle.bmc.loadbalancer.model.BackendSet;
import com.oracle.bmc.loadbalancer.model.LoadBalancer;
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails;
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest;
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest;
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse;
import com.oracle.bmc.model.BmcException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DefaultOracleServerGroupService implements OracleServerGroupService {

  private static final String DEPLOY = "DEPLOY_SERVER_GROUP";
  private static final String DESTROY = "DESTROY_SERVER_GROUP";
  private static final String RESIZE = "RESIZE_SERVER_GROUP";
  private static final String DISABLE = "DISABLE_SERVER_GROUP";
  private static final String ENABLE = "ENABLE_SERVER_GROUP";
  private static final String UpdateLB = "UpdateLoadBalancer";
  private static final int PollingInterval = 5000; //ms
  private static final int PollingTimeout = 10; //minutes

  private final OracleServerGroupPersistence persistence;

  @Autowired
  public DefaultOracleServerGroupService(OracleServerGroupPersistence persistence) {
    this.persistence = persistence;
  }

  @Override
  public List<OracleServerGroup> listAllServerGroups(OracleNamedAccountCredentials creds) {
    OraclePersistenceContext persistenceCtx = new OraclePersistenceContext(creds);
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx);
    return sgNames.stream()
        .map(name -> persistence.getServerGroupByName(persistenceCtx, name))
        .collect(Collectors.toList());
//    return sgNames.findResults { name ->
//      persistence.getServerGroupByName(persistenceCtx, name);
//    }
  }

  String clusterOf(String name) {
    Names names = Names.parseName(name);
    if (names != null) {
      return names.getCluster();
    } else {
      return null;
    }
  }

  String appOf(String name) {
    Names names = Names.parseName(name);
    if (names != null) {
      return names.getApp();
    } else {
      return null;
    }
  }

  @Override
  public List<String> listServerGroupNamesByClusterName(OracleNamedAccountCredentials creds, String clusterName) {
    OraclePersistenceContext persistenceCtx = new OraclePersistenceContext(creds);
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx);
    return sgNames.stream()
        .filter(name -> clusterName.equals(clusterOf(name))).collect(Collectors.toList());
//    return sgNames.findAll { clusterName == Names.parseName(it)?.cluster }
  }

  @Override
  public OracleServerGroup getServerGroup(OracleNamedAccountCredentials creds, String application, String name) {
    OraclePersistenceContext persistenceCtx = new OraclePersistenceContext(creds);
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx);
    List<String> sgNamesInApp = sgNames.stream()
        .filter(it -> application.equals(appOf(it))).collect(Collectors.toList());
//        sgNames.findAll { application == Names.parseName(it)?.app }
    Optional<String> foundName = sgNamesInApp.stream().filter(it -> it.equals(name)).findFirst();
//        sgNamesInApp.find { name == it }
    if (foundName.isPresent()) {
      return persistence.getServerGroupByName(persistenceCtx, name);
    }
    return null;
  }

  @Override
  public void createServerGroup(Task task, OracleServerGroup sg) {
    if(sg.getPlacements() != null && sg.getPlacements().size() > 0) {
      createInstancePool(task, sg);
    } else {
      createInstances(task, sg);
    }
  }

  public void createInstances(Task task, OracleServerGroup sg) {
    Set<OracleInstance> instances = new HashSet<>();
    //if overList createInstance throws com.oracle.bmc.model.BmcException: (400, LimitExceeded, false)
    Set<Exception> errors = new HashSet<>();
    try {
      for (int i = 0; i < sg.getTargetSize(); i++) {
        instances.add(createInstance(sg, i));
      }
    } catch (BmcException e) {
      task.updateStatus(DEPLOY, "Creating instance failed: " + e);
      errors.add(e);
    }
    if (!errors.isEmpty()) {
      if (instances.size() > 0) {
        task.updateStatus(DEPLOY, "ServerGroup created with errors: " + errors);
      } else {
        task.updateStatus(DEPLOY, "ServerGroup creation failed: " + errors);
      }
    }
    if (instances.size() > 0) {
      sg.setInstances(instances);
      persistence.upsertServerGroup(sg);
    }
  }

  @Override
  public void updateServerGroup(OracleServerGroup sg) {
    persistence.upsertServerGroup(sg);
  }

  @Override
  public void deleteServerGroup(OracleServerGroup sg) {
    persistence.deleteServerGroup(sg);
  }

  @Override
  public boolean destroyServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName) {
    OraclePersistenceContext persistenceCtx = new OraclePersistenceContext(creds);
    OracleServerGroup serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName);
    if (serverGroup != null) {
      task.updateStatus(DESTROY, "Found server group: " + serverGroup.getName());
      if (serverGroup.getInstances() != null && serverGroup.getInstances().size() > 0) {
        serverGroup.getInstances().forEach( instance -> {
          if (instance != null) {
            task.updateStatus(DESTROY, "Terminating instance: " + instance.getName());
            terminateInstance(serverGroup, instance);
          }
        });
      }
      task.updateStatus(DESTROY, "Removing persistent data for " + serverGroup.getName());
      persistence.deleteServerGroup(serverGroup);
      return true;
    } else {
      task.updateStatus(DESTROY, "Server group not found");
      return false;
    }
  }

  @Override
  public boolean resizeServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName, Integer targetSize) {
    OraclePersistenceContext persistenceCtx = new OraclePersistenceContext(creds);
    OracleServerGroup serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName);
    if (serverGroup != null) {
      if (serverGroup.getInstancePoolId() != null) {
        ComputeManagementClient client = creds.getComputeManagementClient();
        String compartmentId = creds.getCompartmentId();
        List<UpdateInstancePoolPlacementConfigurationDetails> placementConfig =
            serverGroup.getPlacements().stream().map(it -> Details.update(it)).collect(Collectors.toList());
        UpdateInstancePoolDetails updateDetails = UpdateInstancePoolDetails.builder()
          .instanceConfigurationId(serverGroup.getInstanceConfigurationId())
          .placementConfigurations(placementConfig)
          .size(targetSize).build();
        UpdateInstancePoolResponse res = client.updateInstancePool(UpdateInstancePoolRequest.builder()
          .instancePoolId(serverGroup.getInstancePoolId()).updateInstancePoolDetails(updateDetails).build());
      } else {
        task.updateStatus(RESIZE, "Found serverGroup: " + serverGroupName + " resizing to " + targetSize);
        int currentSize = serverGroup.getTargetSize();
        if (targetSize > currentSize) {
          int numInstancesToCreate = targetSize - currentSize;
          task.updateStatus(RESIZE, "Creating " + numInstancesToCreate + " instances");
          increase(task, serverGroup, targetSize);
        } else if (currentSize > targetSize) {
          int numInstancesToTerminate = currentSize - targetSize;
          task.updateStatus(RESIZE, "Terminating " + numInstancesToTerminate + " instances");
          decrease(task, serverGroup, targetSize);
        } else {
          task.updateStatus(RESIZE, "Already running the desired number of instances");
          return true;
        }
        task.updateStatus(RESIZE, "Updating persistent data for " + serverGroup.getName());
      }
      serverGroup.setTargetSize(targetSize);
      persistence.upsertServerGroup(serverGroup);
      return true;
    } else {
      task.updateStatus(RESIZE, "ServerGroup " + serverGroupName + " not found");
      return false;
    }
  }

  @Override
  public void disableServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName) {
    OraclePersistenceContext persistenceCtx = new OraclePersistenceContext(creds);
    OracleServerGroup serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName);
    if (serverGroup != null) {
      task.updateStatus(DISABLE, "Found server group: " + serverGroup.getName());
      serverGroup.setDisabled(true);
      task.updateStatus(DISABLE, "Updating persistent data for " + serverGroup.getName());
      persistence.upsertServerGroup(serverGroup);
    } else {
      task.updateStatus(DISABLE, "ServerGroup " + serverGroupName + " not found");
    }
  }

  @Override
  public void enableServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName) {
    OraclePersistenceContext persistenceCtx = new OraclePersistenceContext(creds);
    OracleServerGroup serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName);
    if (serverGroup != null) {
      task.updateStatus(ENABLE, "Found server group: " + serverGroup.getName());
      serverGroup.setDisabled(false);
      task.updateStatus(ENABLE, "Updating persistent data for " + serverGroup.getName());
      persistence.upsertServerGroup(serverGroup);
    } else {
      task.updateStatus(ENABLE, "ServerGroup " + serverGroupName + " not found");
    }
  }

  Map<String, String> metadataOf(OracleServerGroup sg) {
    Map<String, String> metadata = new HashMap<>();
    Object sshKeyObject = sg.getLaunchConfig().get("sshAuthorizedKeys");
    String sshAuthorizedKeys = (sshKeyObject != null)? sshKeyObject.toString().trim() : "";
    if (!sshAuthorizedKeys.equals("")) {
      // "ssh_authorized_keys"* - Provide one or more public SSH keys to be included in the ~/.ssh/authorized_keys file
      // for the default user on the instance.
      // Use a newline character to separate multiple keys.
      metadata.put("ssh_authorized_keys", sshAuthorizedKeys);
    }
    return metadata;
  }

  private OracleInstance createInstance(OracleServerGroup sg, int i) {
    LaunchInstanceRequest rq = LaunchInstanceRequest.builder().launchInstanceDetails(LaunchInstanceDetails.builder()
      .availabilityDomain(sg.launchConfig("availabilityDomain"))
      .compartmentId(sg.launchConfig("compartmentId"))
      .imageId(sg.launchConfig("imageId"))
      .shape(sg.launchConfig("shape"))
      .subnetId(sg.launchConfig("subnetId"))
      .metadata(metadataOf(sg))
      .displayName(sg.getName() + "-$i")
      .build()).build();

    LaunchInstanceResponse rs = sg.getCredentials().getComputeClient().launchInstance(rq);
    OracleInstance ins = new OracleInstance();
    ins.setName(rs.getInstance().getDisplayName());
    ins.setId(rs.getInstance().getId());
    ins.setRegion(rs.getInstance().getRegion());
    ins.setZone(rs.getInstance().getAvailabilityDomain());
    ins.setHealthState(HealthState.Starting);
    ins.setCloudProvider(OracleCloudProvider.ID);
    ins.setLaunchTime(rs.getInstance().getTimeCreated().getTime());
    return ins;
  }

  private OracleInstance terminateInstance(OracleServerGroup sg, OracleInstance instance) {
    TerminateInstanceRequest request = TerminateInstanceRequest.builder()
      .instanceId(instance.getId()).build();
    try {
      sg.getCredentials().getComputeClient().terminateInstance(request);
    } catch (BmcException e) {
      // Ignore missing instances (e.g., terminated manually outside of spinnaker)
      if (e.getStatusCode() != 404) {
        throw e;
      }
    }
    return instance;
  }

  private void increase(Task task, OracleServerGroup serverGroup, int targetSize) {
    int currentSize = serverGroup.getTargetSize();
    Set<OracleInstance> instances = new HashSet<>();
    Set<Exception> errors = new HashSet<>();
    for (int i = currentSize; i < targetSize; i++) {
      task.updateStatus(RESIZE, "Creating instance: " + i );
      try {
        instances.add(createInstance(serverGroup, i));
      } catch (BmcException e) {
        task.updateStatus(RESIZE, "Creating instance failed: " + e);
        errors.add(e);
      }
    }
    if (errors.size() > 0) {
      if (instances.size() > 0) {
        task.updateStatus(RESIZE, "ServerGroup resize with errors: " + errors);
      } else {
        task.updateStatus(RESIZE, "ServerGroup resize failed: " + errors);
      }
    }
    for (OracleInstance instance : instances) {
      serverGroup.getInstances().add(instance);
    }
    serverGroup.setTargetSize(currentSize + instances.size());
  }

  private void decrease(Task task, OracleServerGroup serverGroup, int targetSize) {
    Set<OracleInstance> terminated = new HashSet<>();
    int currentSize = serverGroup.getTargetSize();
    Iterator<OracleInstance> instances = serverGroup.getInstances().iterator();
    for (int i = targetSize; i < currentSize; i++) {
      OracleInstance instance = instances.next();
      task.updateStatus(RESIZE, "Terminating instance: " + instance.getName());
      terminated.add(terminateInstance(serverGroup, instance));
    }
    for (OracleInstance instance : terminated) {
        serverGroup.getInstances().remove(instance);
    }
    serverGroup.setTargetSize(targetSize);
  }

  InstanceConfiguration instanceConfig(OracleServerGroup sg) {
    ComputeManagementClient client = sg.getCredentials().getComputeManagementClient();
    String compartmentId = sg.getCredentials().getCompartmentId();
    InstanceConfigurationCreateVnicDetails vnicDetails = InstanceConfigurationCreateVnicDetails.builder().build();

    InstanceConfigurationInstanceSourceViaImageDetails sourceDetails =
      InstanceConfigurationInstanceSourceViaImageDetails.builder().imageId(sg.launchConfig("imageId")).build();

    InstanceConfigurationLaunchInstanceDetails launchDetails = InstanceConfigurationLaunchInstanceDetails.builder()
      .compartmentId(compartmentId)
      .displayName(sg.getName() + "-instance")// instance display name
      .createVnicDetails(vnicDetails)
      .shape(sg.launchConfig("shape"))
      .sourceDetails(sourceDetails)
      .metadata(metadataOf(sg))
      .build();

    ComputeInstanceDetails instanceDetails =
        ComputeInstanceDetails.builder().launchDetails(launchDetails)
            .secondaryVnics(Collections.EMPTY_LIST).blockVolumes(Collections.EMPTY_LIST).build();

    CreateInstanceConfigurationDetails configuDetails =
        CreateInstanceConfigurationDetails.builder().displayName(sg.getName() + "-config")
            .compartmentId(compartmentId).instanceDetails(instanceDetails).build();

    CreateInstanceConfigurationRequest req = CreateInstanceConfigurationRequest.builder()
        .createInstanceConfiguration(configuDetails).build();

    CreateInstanceConfigurationResponse response = client.createInstanceConfiguration(req);
    return response.getInstanceConfiguration();
  }

  /* - create/select(withID) InstanceConfiguration
   * - CreateInstancePoolPlacementConfigurationDetails
   * - desc.targetSize()
   */
  void createInstancePool(Task task, OracleServerGroup sg) {
    InstanceConfiguration instanceConfiguration = instanceConfig(sg);
    ComputeManagementClient client = sg.getCredentials().getComputeManagementClient();
    String compartmentId = sg.getCredentials().getCompartmentId();
    sg.setInstanceConfigurationId(instanceConfiguration.getId());
    CreateInstancePoolDetails createInstancePoolDetails = CreateInstancePoolDetails.builder()
      .displayName(sg.getName())
      .compartmentId(compartmentId).instanceConfigurationId(instanceConfiguration.getId())
      .size(sg.getTargetSize()).placementConfigurations(sg.getPlacements()).build();

    CreateInstancePoolRequest request = CreateInstancePoolRequest.builder()
      .createInstancePoolDetails(createInstancePoolDetails).build();
    //TODO  com.oracle.bmc.model.BmcException: (400, LimitExceeded, false) Max number of instances available for shape VM.Standard2.1, will be exceeded for iad-ad-3 (0), iad-ad-2 (0)
    CreateInstancePoolResponse response = client.createInstancePool(request);
    sg.setInstancePoolId(response.getInstancePool().getId());
    sg.setInstancePool(response.getInstancePool());
    updateServerGroup(sg);
    //TODO what state? sg.instancePool = waitForInstancePool(task, sg, LifecycleState.Running)
  }

  InstancePool waitForInstancePool(Task task, OracleServerGroup sg, LifecycleState targetStates) {
    GetInstancePoolRequest getPoolReq = GetInstancePoolRequest.builder().instancePoolId(sg.getInstancePoolId()).build();
    task.updateStatus(DEPLOY, "Waiting for InstancePool to get to " + targetStates);
    GetInstancePoolResponse poolRes;
    try {
      poolRes = sg.getCredentials().getComputeManagementClient()
        .getWaiters().forInstancePool(getPoolReq, targetStates).execute();
      task.updateStatus(DEPLOY, "InstancePool " + sg.getName() + " lifecycleState $poolRes.instancePool.lifecycleState");
      return poolRes.getInstancePool();
    } catch (Exception e) {
      task.updateStatus(DEPLOY, "Waiting for InstancePool " + sg.getName() + " got error " + e);
      return null;
    }
  }

  /* LifecycleState {
        Provisioning("PROVISIONING"),
        Running("RUNNING"),
        Starting("STARTING"),
        Stopping("STOPPING"),
        Stopped("STOPPED"),
        CreatingImage("CREATING_IMAGE"),
        Terminating("TERMINATING"),
        Terminated("TERMINATED"),
        */
  boolean isGood(String state) {
    return state.equalsIgnoreCase("Provisioning") ||
      state.equalsIgnoreCase("Running") ||
      state.equalsIgnoreCase("Starting");
  }

  @Override
  public void poll(Task task, OracleServerGroup sg) {
    poll(task, sg, PollingInterval, PollingTimeout);
  }

  OracleInstance find(Set<OracleInstance> instances, String id) {
    for (OracleInstance ins : instances) {
      if (id.equals(ins.getId())) {
        return ins;
      }
    }
    return null;
  }

  String getPrivateIp(OracleServerGroup sg, String instanceId) {
    String compartmentId = sg.getCredentials().getCompartmentId();
    ListVnicAttachmentsResponse vnicAttachRes = sg.getCredentials().getComputeClient()
      .listVnicAttachments(ListVnicAttachmentsRequest.builder()
      .compartmentId(compartmentId).instanceId(instanceId).build());
    for(VnicAttachment vnicAttach: vnicAttachRes.getItems()) {
      Vnic vnic = sg.getCredentials().getNetworkClient().getVnic(GetVnicRequest.builder()
          .vnicId(vnicAttach.getVnicId()).build()).getVnic();
      String privateIp = vnic.getPrivateIp();
      if (privateIp != null)  {
        return privateIp;
      }
    }
    return null;
  }

  Set<String> addressesOf(Set<OracleInstance> instances) {
    return instances.stream().filter(it -> it.getPrivateIp() != null)
      .map(it -> it.getPrivateIp()).collect(Collectors.toSet());
  }

  void syncInstances(Task task, OracleServerGroup sg) {
    String compartmentId = sg.getCredentials().getCompartmentId();
    Set<OracleInstance> oldInstances = sg.getInstances().stream().collect(Collectors.toSet());
    Set<OracleInstance> newInstances = new HashSet<>();
    Set<OracleInstance> instaceCache = oldInstances.stream().collect(Collectors.toSet());
    try {
      ListInstancePoolInstancesResponse listRes = sg.getCredentials().getComputeManagementClient().listInstancePoolInstances(
        ListInstancePoolInstancesRequest.builder().compartmentId(compartmentId).instancePoolId(sg.getInstancePoolId()).build());
      listRes.getItems().forEach( ins -> {
        if (isGood(ins.getState())) {
          OracleInstance instance = find(instaceCache, ins.getId());
          Instance.LifecycleState lifecycleState = Instance.LifecycleState.valueOf(ins.getState());
          if (instance == null) {
            instance = new OracleInstance();
            instance.setName(ins.getDisplayName());
            instance.setId(ins.getId());
            instance.setRegion(ins.getRegion());
            instance.setZone(ins.getAvailabilityDomain());
            instance.setLifecycleState(lifecycleState);
            instance.setCloudProvider(OracleCloudProvider.ID);
            instance.setLaunchTime(ins.getTimeCreated().getTime());
            instaceCache.add(instance);
          } else {
            instance.setLifecycleState(lifecycleState);
          }
          newInstances.add(instance);
          if (instance.getPrivateIp() == null) {
            String privateIp = getPrivateIp(sg, ins.getId());
            if (privateIp != null) {
              instance.setPrivateIp(privateIp);
            }
          }
        }
      });
    } catch (BmcException e) {
      if (e.getStatusCode() != 404) {
        throw e;
      }
    }
    if (!addressesOf(newInstances).equals(addressesOf(oldInstances))) {
      sg.setInstances(newInstances);
      updateServerGroup(sg);
      updateLoadBalancer(task, sg, oldInstances, newInstances);
    }
  }

  void poll(Task task, OracleServerGroup sg, int intervalMillis, int timeoutMinutes) {
    String compartmentId = sg.getCredentials().getCompartmentId();
    long finishBy = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(timeoutMinutes);
    InstancePool instancePool = sg.getCredentials().getComputeManagementClient().getInstancePool(
        GetInstancePoolRequest.builder().instancePoolId(sg.getInstancePoolId()).build()).getInstancePool();
    if (instancePool != null) {
      sg.setInstancePool(instancePool);
    } else {
      task.updateStatus(RESIZE, "InstancePool " + sg.getName() + " did not exist...continuing");
    }
    int currentSize = addressesOf(sg.getInstances()).size();
    if (currentSize != sg.getInstancePool().getSize()) {
      syncInstances(task, sg);
      currentSize = addressesOf(sg.getInstances()).size();
    }

    while (currentSize != sg.getTargetSize() && System.currentTimeMillis() < finishBy) {
      task.updateStatus(RESIZE, "InstancePool " + sg.getName() + " has " + currentSize + " instances up targeting " + sg.getTargetSize());
      syncInstances(task, sg);
      currentSize = addressesOf(sg.getInstances()).size();
      if (currentSize != sg.getTargetSize()) {
        task.updateStatus(RESIZE, "Waiting for InstancePool " + sg.getName() + "(" + currentSize + ") to have " + sg.getTargetSize() + " Up instances");
        try {
          Thread.sleep(intervalMillis);
        } catch (InterruptedException e) {
        }
      }
    }
  }

  @Override
  public void updateLoadBalancer(Task task, OracleServerGroup serverGroup,
    Set<OracleInstance> oldInstances, Set<OracleInstance> newInstances) {
    Set<String> oldGroup = addressesOf(oldInstances);
    Set<String> newGroup = addressesOf(newInstances);
    if (serverGroup.getBackendSetName()  == null || newGroup.equals(oldGroup)) {
      return;
    }
    LoadBalancer loadBalancer = null;
    if (serverGroup.getLoadBalancerId() != null) {
      loadBalancer = serverGroup.getCredentials().getLoadBalancerClient().getLoadBalancer(
        GetLoadBalancerRequest.builder().loadBalancerId(serverGroup.getLoadBalancerId()).build()).getLoadBalancer();
    } else {
      return;
    }
    if (loadBalancer == null) {
      task.updateStatus(UpdateLB, "LoadBalancer " + serverGroup.getLoadBalancerId() + " did not exist...continuing");
      return;
    }
    task.updateStatus(UpdateLB, "Updating LoadBalancer(" + loadBalancer.getDisplayName() + ") " +
        "BackendSet(" + serverGroup.getBackendSetName() + ") from " + oldGroup + " to " + newGroup );
    try {
      BackendSet backendSet = loadBalancer.getBackendSets().get(serverGroup.getBackendSetName());
      if (backendSet == null ) {
        task.updateStatus(UpdateLB, "BackendSet(" + serverGroup.getBackendSetName() + ") did not exist...continuing");
        return;
      }
      // existing backends but not in the oldGroup(to be removed)
      List<BackendDetails> backends = backendSet.getBackends().stream()
        .filter( it -> !oldGroup.contains(it.getIpAddress()) )
        .map( it -> Details.of(it) ).collect(Collectors.toList());
      for(String ip : newGroup) {
        backends.add(BackendDetails.builder().ipAddress(ip).port(backendSet.getHealthChecker().getPort()).build());
      }
      UpdateBackendSetDetails.Builder details = UpdateBackendSetDetails.builder().backends(backends);
      if (backendSet.getSslConfiguration() != null) {
        details.sslConfiguration(Details.of((backendSet.getSslConfiguration())));
      }
      if (backendSet.getSessionPersistenceConfiguration() != null) {
        details.sessionPersistenceConfiguration(backendSet.getSessionPersistenceConfiguration());
      }
      if (backendSet.getHealthChecker() != null) {
        details.healthChecker(Details.of(backendSet.getHealthChecker()));
      }
      if (backendSet.getPolicy() != null) {
        details.policy(backendSet.getPolicy());
      }
      UpdateBackendSetRequest updateBackendSet = UpdateBackendSetRequest.builder()
        .loadBalancerId(serverGroup.getLoadBalancerId()).backendSetName(backendSet.getName())
        .updateBackendSetDetails(details.build()).build();
      task.updateStatus(UpdateLB, "Updating backendSet ${backendSet.name}");
      UpdateBackendSetResponse updateRes = serverGroup.getCredentials().getLoadBalancerClient().updateBackendSet(updateBackendSet);
      OracleWorkRequestPoller.poll(updateRes.getOpcWorkRequestId(), UpdateLB, task, serverGroup.getCredentials().getLoadBalancerClient());
   } catch (BmcException e) {
     if (e.getStatusCode() == 404) {
        task.updateStatus(UpdateLB, "BackendSet ${serverGroup.backendSetName} did not exist...continuing");
      } else {
        throw e;
      }
    }
  }
}

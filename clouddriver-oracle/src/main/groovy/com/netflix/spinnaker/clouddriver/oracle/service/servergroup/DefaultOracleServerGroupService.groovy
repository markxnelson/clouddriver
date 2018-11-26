/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.service.servergroup

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.model.Details
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.core.ComputeManagementClient
import com.oracle.bmc.core.model.ComputeInstanceDetails
import com.oracle.bmc.core.model.CreateInstanceConfigurationDetails
import com.oracle.bmc.core.model.CreateInstancePoolDetails
import com.oracle.bmc.core.model.InstanceConfiguration
import com.oracle.bmc.core.model.InstanceConfigurationCreateVnicDetails
import com.oracle.bmc.core.model.InstanceConfigurationInstanceSourceViaImageDetails
import com.oracle.bmc.core.model.InstanceConfigurationLaunchInstanceDetails
import com.oracle.bmc.core.model.InstancePool
import com.oracle.bmc.core.model.LaunchInstanceDetails
import com.oracle.bmc.core.model.UpdateInstancePoolDetails
import com.oracle.bmc.core.model.Vnic
import com.oracle.bmc.core.model.InstancePool.LifecycleState
import com.oracle.bmc.core.requests.CreateInstanceConfigurationRequest
import com.oracle.bmc.core.requests.CreateInstancePoolRequest
import com.oracle.bmc.core.requests.GetInstancePoolRequest
import com.oracle.bmc.core.requests.GetVnicRequest
import com.oracle.bmc.core.requests.LaunchInstanceRequest
import com.oracle.bmc.core.requests.ListInstancePoolInstancesRequest
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest
import com.oracle.bmc.core.requests.TerminateInstanceRequest
import com.oracle.bmc.core.requests.UpdateInstancePoolRequest
import com.oracle.bmc.core.responses.CreateInstanceConfigurationResponse
import com.oracle.bmc.core.responses.CreateInstancePoolResponse
import com.oracle.bmc.core.responses.GetInstancePoolResponse
import com.oracle.bmc.core.responses.ListInstancePoolInstancesResponse
import com.oracle.bmc.core.responses.ListVnicAttachmentsResponse
import com.oracle.bmc.core.responses.UpdateInstancePoolResponse
import com.oracle.bmc.loadbalancer.model.BackendDetails
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.model.BmcException
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DefaultOracleServerGroupService implements OracleServerGroupService {

  private static final String DEPLOY = "DEPLOY_SERVER_GROUP"
  private static final String DESTROY = "DESTROY_SERVER_GROUP"
  private static final String RESIZE = "RESIZE_SERVER_GROUP"
  private static final String DISABLE = "DISABLE_SERVER_GROUP"
  private static final String ENABLE = "ENABLE_SERVER_GROUP"
  private static final String UpdateLB = "UpdateLoadBalancer"
  private static final int PollingInterval = 5000; //ms
  private static final int PollingTimeout = 10; //minutes

  private final OracleServerGroupPersistence persistence;

  @Autowired
  public DefaultOracleServerGroupService(OracleServerGroupPersistence persistence) {
    this.persistence = persistence
  }

  @Override
  List<OracleServerGroup> listAllServerGroups(OracleNamedAccountCredentials creds) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx)
    return sgNames.findResults { name ->
      persistence.getServerGroupByName(persistenceCtx, name)
    }
  }

  @Override
  List<String> listServerGroupNamesByClusterName(OracleNamedAccountCredentials creds, String clusterName) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx)
    return sgNames.findAll { clusterName == Names.parseName(it)?.cluster }
  }

  @Override
  OracleServerGroup getServerGroup(OracleNamedAccountCredentials creds, String application, String name) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    List<String> sgNames = persistence.listServerGroupNames(persistenceCtx)
    List<String> sgNamesInApp = sgNames.findAll { application == Names.parseName(it)?.app }
    String foundName = sgNamesInApp.find { name == it }
    if (foundName) {
      return persistence.getServerGroupByName(persistenceCtx, name)
    }
    return null;
  }

  @Override
  void createServerGroup(Task task, OracleServerGroup sg) {
    if(sg.placements) {
      createInstancePool(task, sg)
    } else {
      createInstances(task, sg)
    }
  }

  void createInstances(Task task, OracleServerGroup sg) {
    def instances = [] as Set
    //if overList createInstance throws com.oracle.bmc.model.BmcException: (400, LimitExceeded, false)
    def errors = []
    try {
      for (int i = 0; i < sg.targetSize; i++) {
        instances << createInstance(sg, i)
      }
    } catch (BmcException e) {
      task.updateStatus DEPLOY, "Creating instance failed: $e"
      errors << e
    }
    if (errors) {
      if (instances.size() > 0) {
        task.updateStatus DEPLOY, "ServerGroup created with errors: $errors"
      } else {
        task.updateStatus DEPLOY, "ServerGroup creation failed: $errors"
      }
    }
    if (instances.size() > 0) {
      sg.instances = instances
      persistence.upsertServerGroup(sg)
    }
  }

  @Override
  void updateServerGroup(OracleServerGroup sg) {
    persistence.upsertServerGroup(sg)
  }

  @Override
  void deleteServerGroup(OracleServerGroup sg) {
    persistence.deleteServerGroup(sg)
  }

  @Override
  boolean destroyServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    def serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      task.updateStatus DESTROY, "Found server group: $serverGroup.name"
      if (serverGroup.instances && serverGroup.instances.size() > 0) {
        for (int i = 0; i < serverGroup.targetSize; i++) {
          def instance = serverGroup.instances[i]
          if (instance) {
            task.updateStatus DESTROY, "Terminating instance: $instance.name"
            terminateInstance(serverGroup, instance)
          }
        }
      }
      task.updateStatus DESTROY, "Removing persistent data for $serverGroup.name"
      persistence.deleteServerGroup(serverGroup)
      return true
    } else {
      task.updateStatus DESTROY, "Server group not found"
      return false
    }
  }

  @Override
  boolean resizeServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName, Integer targetSize) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    OracleServerGroup serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      if (serverGroup.instancePoolId != null) {
        ComputeManagementClient client = creds.computeManagementClient
        String compartmentId = creds.compartmentId
        UpdateInstancePoolDetails updateDetails = UpdateInstancePoolDetails.builder()
          .instanceConfigurationId(serverGroup.instanceConfigurationId)
          .placementConfigurations(serverGroup.placements.collect{Details.update(it)} as List)
          .size(targetSize).build()
        UpdateInstancePoolResponse res = client.updateInstancePool(UpdateInstancePoolRequest.builder()
          .instancePoolId(serverGroup.instancePoolId).updateInstancePoolDetails(updateDetails).build())
      } else {
        task.updateStatus RESIZE, "Found server group: $serverGroup.name resizing to $targetSize"
        if (targetSize > serverGroup.targetSize) {
          int numInstancesToCreate = targetSize - serverGroup.targetSize
          task.updateStatus RESIZE, "Creating $numInstancesToCreate instances"
          increase(task, serverGroup, targetSize)
        } else if (serverGroup.targetSize > targetSize) {
          int numInstancesToTerminate = serverGroup.targetSize - targetSize
          task.updateStatus RESIZE, "Terminating $numInstancesToTerminate instances"
          decrease(task, serverGroup, targetSize)
        } else {
          task.updateStatus RESIZE, "Already running the desired number of instances"
          return true
        }
        task.updateStatus RESIZE, "Updating persistent data for $serverGroup.name"
      }
      serverGroup.targetSize = targetSize
      persistence.upsertServerGroup(serverGroup)
      return true
    } else {
      task.updateStatus RESIZE, "Server group not found"
      return false
    }
  }

  @Override
  void disableServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    def serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      task.updateStatus DISABLE, "Found server group: $serverGroup.name"
      serverGroup.disabled = true
      task.updateStatus DISABLE, "Updating persistent data for $serverGroup.name"
      persistence.upsertServerGroup(serverGroup)
    } else {
      task.updateStatus DISABLE, "Server group not found"
    }
  }

  @Override
  void enableServerGroup(Task task, OracleNamedAccountCredentials creds, String serverGroupName) {
    def persistenceCtx = new OraclePersistenceContext(creds)
    def serverGroup = persistence.getServerGroupByName(persistenceCtx, serverGroupName)
    if (serverGroup != null) {
      task.updateStatus ENABLE, "Found server group: $serverGroup.name"
      serverGroup.disabled = false
      task.updateStatus ENABLE, "Updating persistent data for $serverGroup.name"
      persistence.upsertServerGroup(serverGroup)
    } else {
      task.updateStatus ENABLE, "Server group not found"
    }
  }

  private OracleInstance createInstance(OracleServerGroup sg, int i) {
    Map<String, String> metadata = new HashMap<>()
    if (sg.launchConfig["sshAuthorizedKeys"]?.trim()) {
      // "ssh_authorized_keys"* - Provide one or more public SSH keys to be included in the ~/.ssh/authorized_keys file
      // for the default user on the instance.
      // Use a newline character to separate multiple keys.
      metadata.put("ssh_authorized_keys", sg.launchConfig["sshAuthorizedKeys"] as String)
    }

    LaunchInstanceRequest rq = LaunchInstanceRequest.builder().launchInstanceDetails(LaunchInstanceDetails.builder()
      .availabilityDomain(sg.launchConfig["availabilityDomain"] as String)
      .compartmentId(sg.launchConfig["compartmentId"] as String)
      .imageId(sg.launchConfig["imageId"] as String)
      .shape(sg.launchConfig["shape"] as String)
      .subnetId(sg.launchConfig["subnetId"] as String)
      .metadata(metadata)
      .displayName(sg.name + "-$i")
      .build()).build()

    def rs = sg.credentials.computeClient.launchInstance(rq)
    return new OracleInstance(
      name: rs.instance.displayName,
      id: rs.instance.id,
      region: rs.instance.region,
      zone: rs.instance.availabilityDomain,
      healthState: HealthState.Starting,
      cloudProvider: OracleCloudProvider.ID,
      launchTime: rs.instance.timeCreated.time)
  }

  private OracleInstance terminateInstance(OracleServerGroup sg, OracleInstance instance) {
    TerminateInstanceRequest request = TerminateInstanceRequest.builder()
      .instanceId(instance.id).build()
    try {
      sg.credentials.computeClient.terminateInstance(request)
    } catch (BmcException e) {
      // Ignore missing instances (e.g., terminated manually outside of spinnaker)
      if (e.statusCode != 404) {
        throw e
      }
    }
    return instance
  }

  private void increase(Task task, OracleServerGroup serverGroup, int targetSize) {
    int currentSize = serverGroup.targetSize;
    def instances = [] as Set
    def errors = []
    for (int i = currentSize; i < targetSize; i++) {
      task.updateStatus RESIZE, "Creating instance: $i"
      try {
        instances << createInstance(serverGroup, i)
      } catch (BmcException e) {
        task.updateStatus RESIZE, "Creating instance failed: $e"
        errors << e
      }
    }
    if (errors) {
      if (instances.size() > 0) {
        task.updateStatus RESIZE, "ServerGroup resize with errors: $errors"
      } else {
        task.updateStatus RESIZE, "ServerGroup resize failed: $errors"
      }
    }
    for (OracleInstance instance : instances) {
      serverGroup.instances.add(instance)
    }
    serverGroup.targetSize = currentSize + instances.size()
  }

  private void decrease(Task task, OracleServerGroup serverGroup, int targetSize) {
    def instances = [] as Set
    int currentSize = serverGroup.targetSize;
    for (int i = targetSize; i < currentSize; i++) {
      task.updateStatus RESIZE, "Terminating instance: " + serverGroup.instances[i].name
      instances << terminateInstance(serverGroup, serverGroup.instances[i])
    }
    for (OracleInstance instance : instances) {
        serverGroup.instances.remove(instance)
    }
    serverGroup.targetSize = targetSize
  }

  InstanceConfiguration instanceConfig(OracleServerGroup sg) {
    ComputeManagementClient client = sg.credentials.computeManagementClient
    String compartmentId = sg.credentials.compartmentId
    InstanceConfigurationCreateVnicDetails vnicDetails = InstanceConfigurationCreateVnicDetails.builder().build()

    InstanceConfigurationInstanceSourceViaImageDetails sourceDetails =
      InstanceConfigurationInstanceSourceViaImageDetails.builder().imageId(sg.launchConfig.imageId).build()

    InstanceConfigurationLaunchInstanceDetails launchDetails =
        InstanceConfigurationLaunchInstanceDetails.builder().compartmentId(compartmentId)
            .displayName(sg.name + "-instance")// instance display name
            .createVnicDetails(vnicDetails).shape(sg.launchConfig.shape).sourceDetails(sourceDetails)
            .build()

    ComputeInstanceDetails instanceDetails =
        ComputeInstanceDetails.builder().launchDetails(launchDetails)
            .secondaryVnics(Collections.EMPTY_LIST).blockVolumes(Collections.EMPTY_LIST).build();

    CreateInstanceConfigurationDetails configuDetails =
        CreateInstanceConfigurationDetails.builder().displayName(sg.name + "-config")
            .compartmentId(compartmentId).instanceDetails(instanceDetails).build()

    CreateInstanceConfigurationRequest req = CreateInstanceConfigurationRequest.builder()
        .createInstanceConfiguration(configuDetails).build()

    CreateInstanceConfigurationResponse response = client.createInstanceConfiguration(req);
    return response.getInstanceConfiguration()
  }

  /* - create/select(withID) InstanceConfiguration
   * - CreateInstancePoolPlacementConfigurationDetails
   * - desc.targetSize()
   */
  void createInstancePool(Task task, OracleServerGroup sg) {
    InstanceConfiguration instanceConfiguration = instanceConfig(sg)
    ComputeManagementClient client = sg.credentials.computeManagementClient
    String compartmentId = sg.credentials.compartmentId
    sg.instanceConfigurationId = instanceConfiguration.id
    CreateInstancePoolDetails createInstancePoolDetails = CreateInstancePoolDetails.builder()
        // instancePool dispalyName
      .displayName(sg.name)
      .compartmentId(compartmentId).instanceConfigurationId(instanceConfiguration.id)
      .size(sg.targetSize).placementConfigurations(sg.placements).build()

    CreateInstancePoolRequest request = CreateInstancePoolRequest.builder()
      .createInstancePoolDetails(createInstancePoolDetails).build()
    //TODO  com.oracle.bmc.model.BmcException: (400, LimitExceeded, false) Max number of instances available for shape VM.Standard2.1, will be exceeded for iad-ad-3 (0), iad-ad-2 (0)
    CreateInstancePoolResponse response = client.createInstancePool(request)
    sg.instancePoolId = response.instancePool.id
    sg.instancePool = response.instancePool
    updateServerGroup(sg)
    //TODO what state? sg.instancePool = waitForInstancePool(task, sg, LifecycleState.Running)
  }

  InstancePool waitForInstancePool(Task task, OracleServerGroup sg, LifecycleState targetStates) {
    GetInstancePoolRequest getPoolReq = GetInstancePoolRequest.builder().instancePoolId(sg.instancePoolId).build()
    task.updateStatus(DEPLOY, "Waiting for InstancePool to get to $targetStates")
    GetInstancePoolResponse poolRes =  sg.credentials.computeManagementClient.waiters.forInstancePool(getPoolReq, targetStates).execute()
    task.updateStatus(DEPLOY, "InstancePool $sg.name lifecycleState $poolRes.instancePool.lifecycleState")
    poolRes.instancePool
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
    state.equalsIgnoreCase("Provisioning") ||
    state.equalsIgnoreCase("Running") ||
    state.equalsIgnoreCase("Starting")
  }

  void poll(Task task, OracleServerGroup sg) {
    poll(task, sg, PollingInterval, PollingTimeout)
  }

  void syncInstances(Task task, OracleServerGroup sg) {
    String compartmentId = sg.credentials.compartmentId
    Set<OracleInstance> oldInstances = sg.instances?.collect{ it }?: [] as Set
    Set<OracleInstance> newInstances = []
    Set<OracleInstance> instaceCache = oldInstances.collect{ it }
    try {
      ListInstancePoolInstancesResponse listRes = sg.credentials.computeManagementClient.listInstancePoolInstances(
        ListInstancePoolInstancesRequest.builder().compartmentId(compartmentId).instancePoolId(sg.instancePoolId).build())
      listRes.items.each { ins ->
        if (isGood(ins.state)) {
          OracleInstance instance = instaceCache.find{ it.id == ins.id }
          if (instance == null) {
            instance = new OracleInstance (
              name: ins.displayName,
              id: ins.id,
              region: ins.region,
              zone: ins.availabilityDomain,
              lifecycleState: ins.state,
              cloudProvider: OracleCloudProvider.ID,
              launchTime: ins.timeCreated.fastTime)
            instaceCache << instance
          } else {
            instance.lifecycleState = ins.state
          }
          newInstances << instance
          if (instance.privateIp == null) {
            ListVnicAttachmentsResponse vnicAttachRes = sg.credentials.computeClient
              .listVnicAttachments(ListVnicAttachmentsRequest.builder()
              .compartmentId(compartmentId).instanceId(ins.id).build())
            vnicAttachRes.items.each { vnicAttach ->
              Vnic vnic = sg.credentials.networkClient.getVnic(GetVnicRequest.builder()
                .vnicId(vnicAttach.vnicId).build()).vnic
              if (vnic.privateIp) {
                instance.privateIp = vnic.privateIp
              }
            }
          }
        }
      }
    } catch (BmcException e) {
      if (e.statusCode != 404) {
        throw e
      }
    }
    if (!addressesOf(newInstances).equals(addressesOf(oldInstances))) {
      sg.instances = newInstances
System.out.println( '~~~  updating   ' + sg.instances)
      updateServerGroup(sg)
      updateLoadBalancer(task, sg, oldInstances, newInstances)
    }
//    return newInstances
  }

  void poll(Task task, OracleServerGroup sg, int intervalMillis, int timeoutMinutes) {
    String compartmentId = sg.credentials.compartmentId
    long finishBy = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(timeoutMinutes)
    sg.instancePool = sg.credentials.computeManagementClient
      .getInstancePool(GetInstancePoolRequest.builder().instancePoolId(sg.instancePoolId).build()).instancePool

    // This must work for both scaling up and down
    int currentSize = addressesOf(sg.instances).size()

    if (currentSize != sg.instancePool.size) {
System.out.println( '~~~  syncing1   ' + sg.instances)
      syncInstances(task, sg)
System.out.println( '~~~  syncing2   ' + sg.instances)
      currentSize = addressesOf(sg.instances).size()
    }

    while (currentSize != sg.targetSize && System.currentTimeMillis() < finishBy) {
      syncInstances(task, sg)
      currentSize = addressesOf(sg.instances).size()
      task.updateStatus RESIZE, "${sg.name} currently has ${currentSize} instances up"


System.out.println( '~~~  ?? currentSize  ' + currentSize + ' ?? targetSize ' + sg.targetSize)

      if (currentSize != sg.targetSize) {
        task.updateStatus RESIZE, "Waiting for ${sg.name} serverGroup(${currentSize}) to get to ${sg.targetSize} Up instances"
        Thread.sleep(intervalMillis)
      }
    }
  }

  Set<String> addressesOf(Set<OracleInstance> instances) {
    return instances.findAll{ it.privateIp != null }.collect{ it.privateIp } as Set
  }

  void updateLoadBalancer(Task task, OracleServerGroup serverGroup,
    Set<OracleInstance> oldInstances, Set<OracleInstance> newInstances) {
    Set<String> oldGroup = addressesOf(oldInstances)
    Set<String> newGroup = addressesOf(newInstances)

System.out.println( '~~~?? oldGroup ' + oldGroup)
System.out.println( '~~~?? newGroup ' + newGroup)
System.out.println( '~~~?? newInsts ' + newInstances)

    if (newGroup.equals(oldGroup)) {
      return
    }
    LoadBalancer loadBalancer = serverGroup?.loadBalancerId? serverGroup.credentials.loadBalancerClient.getLoadBalancer(
      GetLoadBalancerRequest.builder().loadBalancerId(serverGroup.loadBalancerId).build())?.getLoadBalancer() : null
    task.updateStatus UpdateLB, "Updating LoadBalancer(${loadBalancer?.displayName}) BackendSet(${serverGroup?.backendSetName})"
    if (loadBalancer) {
      try {
        BackendSet backendSet = serverGroup.backendSetName? loadBalancer.backendSets.get(serverGroup.backendSetName) : null
        if (backendSet == null && loadBalancer.backendSets.size() == 1) {
          backendSet = loadBalancer.backendSets.values().first();
        }
        if (backendSet) {
          // existing backends but not in the oldGroup(to be removed)
          List<BackendDetails> backends = backendSet.backends.findAll { !oldGroup.contains(it.ipAddress) } .collect { Details.of(it) }
          newGroup.each {
            backends << BackendDetails.builder().ipAddress(it).port(backendSet.healthChecker.port).build()
          }
          UpdateBackendSetDetails.Builder details = UpdateBackendSetDetails.builder().backends(backends)
          if (backendSet.sslConfiguration) {
            details.sslConfiguration(Details.of(backendSet.sslConfiguration))
          }
          if (backendSet.sessionPersistenceConfiguration) {
            details.sessionPersistenceConfiguration(backendSet.sessionPersistenceConfiguration)
          }
          if (backendSet.healthChecker) {
            details.healthChecker(Details.of(backendSet.healthChecker))
          }
          if (backendSet.policy) {
            details.policy(backendSet.policy)
          }
          UpdateBackendSetRequest updateBackendSet = UpdateBackendSetRequest.builder()
            .loadBalancerId(serverGroup.loadBalancerId).backendSetName(backendSet.name)
            .updateBackendSetDetails(details.build()).build()
          task.updateStatus UpdateLB, "Updating backendSet ${backendSet.name}"
          def updateRes = serverGroup.credentials.loadBalancerClient.updateBackendSet(updateBackendSet)
          OracleWorkRequestPoller.poll(updateRes.opcWorkRequestId, UpdateLB, task, serverGroup.credentials.loadBalancerClient)
        }
      } catch (BmcException e) {
        if (e.statusCode == 404) {
          task.updateStatus UpdateLB, "BackendSet ${serverGroup.backendSetName} did not exist...continuing"
        } else {
          throw e
        }
      }
    }
  }
}

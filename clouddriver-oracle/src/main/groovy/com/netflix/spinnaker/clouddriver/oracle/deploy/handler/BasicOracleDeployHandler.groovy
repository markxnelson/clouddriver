/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.handler

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.BasicOracleDeployDescription
import com.netflix.spinnaker.clouddriver.oracle.model.Details
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.oracle.bmc.core.ComputeManagementClient
import com.oracle.bmc.core.model.ComputeInstanceDetails
import com.oracle.bmc.core.model.CreateInstanceConfigurationDetails
import com.oracle.bmc.core.model.CreateInstancePoolDetails
import com.oracle.bmc.core.model.InstanceConfiguration
import com.oracle.bmc.core.model.InstanceConfigurationCreateVnicDetails
import com.oracle.bmc.core.model.InstanceConfigurationInstanceSourceViaImageDetails
import com.oracle.bmc.core.model.InstanceConfigurationLaunchInstanceDetails
import com.oracle.bmc.core.model.InstancePool
import com.oracle.bmc.core.model.Vnic
import com.oracle.bmc.core.requests.CreateInstanceConfigurationRequest
import com.oracle.bmc.core.requests.CreateInstancePoolRequest
import com.oracle.bmc.core.requests.GetInstancePoolRequest
import com.oracle.bmc.core.requests.GetVnicRequest
import com.oracle.bmc.core.requests.ListInstancePoolInstancesRequest
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest
import com.oracle.bmc.core.responses.CreateInstanceConfigurationResponse
import com.oracle.bmc.core.responses.CreateInstancePoolResponse
import com.oracle.bmc.core.responses.ListInstancePoolInstancesResponse
import com.oracle.bmc.core.responses.ListVnicAttachmentsResponse
import com.oracle.bmc.loadbalancer.model.BackendDetails
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class BasicOracleDeployHandler implements DeployHandler<BasicOracleDeployDescription> {

  private static final String BASE_PHASE = "DEPLOY"
  private static final int CreateInstancePoolTimeout = 30;

  @Autowired
  OracleServerGroupService oracleServerGroupService

  @Autowired
  OracleClusterProvider clusterProvider

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicOracleDeployDescription
  }

  OracleServerGroup updateInstances(BasicOracleDeployDescription desc, OracleServerGroup sg, InstancePool inpool, int targetSize) {
    String compartmentId = desc.credentials.compartmentId
    long finishBy = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(CreateInstancePoolTimeout)
    Set<String> up = []
    while (up.size() < targetSize && System.currentTimeMillis() < finishBy) {
      inpool = desc.credentials.computeManagementClient
        .getInstancePool(GetInstancePoolRequest.builder().instancePoolId(inpool.id).build()).instancePool
      System.out.println('~~~~~~~ CreateInstancePool lifecycleState : ' + inpool.lifecycleState )
      ListInstancePoolInstancesResponse listRes = desc.credentials.computeManagementClient
        .listInstancePoolInstances(ListInstancePoolInstancesRequest.builder().compartmentId(compartmentId).instancePoolId(sg.instancePoolId).build())
      System.out.println('~~~~~~~ CreateInstancePool instances : ' + listRes.items )
      listRes.items.each { ins ->
        System.out.println('~~~~~~~ ins : ' + ins.id)
        System.out.println('~~~~~~~ ins : ' + ins.state)
        OracleInstance instance = sg.instances.find{ it.id == ins.id }
        if (instance == null) {
          instance = new OracleInstance (
            name: ins.displayName,
            id: ins.id,
            region: ins.region,
            zone: ins.availabilityDomain,
            lifecycleState: ins.state,
            cloudProvider: OracleCloudProvider.ID,
            launchTime: ins.timeCreated.fastTime)
          sg.instances << instance
        }

        System.out.println('~~~~~~~ sg.instances : ' + sg.instances)

        ListVnicAttachmentsResponse vnicAttachRs = desc.credentials.computeClient
          .listVnicAttachments(ListVnicAttachmentsRequest.builder()
          .compartmentId(compartmentId).instanceId(ins.id).build())
        vnicAttachRs.items.each { vnicAttach ->
          Vnic vnic = desc.credentials.networkClient.getVnic(GetVnicRequest.builder()
          .vnicId(vnicAttach.vnicId).build()).vnic
          System.out.println('~~~~~~~ vnic.privateIp : ' + vnic.privateIp)
          if (vnic.privateIp) {
            instance.privateIp = vnic.privateIp
            up << vnic.privateIp
          }
          System.out.println('~~~~~~!!!!!! up : ' + up.size()+ ' ' + up)
        }
      }
      task.updateStatus BASE_PHASE, "Waiting for server group instances to get to Up state"
      Thread.sleep(5000)
    }
    return sg
  }

  @Override
  DeploymentResult handle(BasicOracleDeployDescription description, List priorOutputs) {
    def region = description.region
    def serverGroupNameResolver = new OracleServerGroupNameResolver(oracleServerGroupService, description.credentials, region)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster $clusterName ..."

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)

    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    Map<String, Object> launchConfig = [
      "availabilityDomain": description.availabilityDomain,
      "compartmentId"     : description.credentials.compartmentId,
      "imageId"           : description.imageId,
      "shape"             : description.shape,
      "vpcId"             : description.vpcId,
      "subnetId"          : description.subnetId,
      "sshAuthorizedKeys" : description.sshAuthorizedKeys,
      "placements"        : description.placements?.collect {it.primarySubnetId +',' + it.availabilityDomain},
      "createdTime"       : System.currentTimeMillis()
    ]
    int targetSize = description.targetSize?: (description.capacity?.desired?:0)
    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName with $targetSize instance(s) "

    def sg = new OracleServerGroup(
      name: serverGroupName,
      region: description.region,
      zone: description.availabilityDomain,
      launchConfig: launchConfig,
      targetSize: targetSize,
      credentials: description.credentials,
      loadBalancerId: description.loadBalancerId,
      placements: description.placements
    )

    if (description.placements) {
      InstancePool inpool = createInstancePool(serverGroupName, description)
      sg.instancePoolId = inpool.id
      sg.instanceConfigurationId = inpool.instanceConfigurationId
      sg.instancePool = inpool
System.out.println('~~~~~~~ CreateInstancePool sg.instancePoolId : ' + sg.instancePoolId )
System.out.println('~~~~~~~                       lifecycleState : ' + inpool.lifecycleState )
      oracleServerGroupService.updateServerGroup(sg)
      sg = updateInstances(description, sg, inpool, targetSize)
      oracleServerGroupService.updateServerGroup(sg)
      if (sg.instances.size() < targetSize) {
        task.updateStatus(BASE_PHASE, "Timed out waiting for server group instances to get to Up state")
        task.fail()
        return
      }
    }  else {
      oracleServerGroupService.createServerGroup(task, sg)
    }

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName."

    if (description.loadBalancerId) {
      // get LB
      LoadBalancer lb = description.credentials.loadBalancerClient.getLoadBalancer(
        GetLoadBalancerRequest.builder().loadBalancerId(description.loadBalancerId).build()).loadBalancer

      task.updateStatus BASE_PHASE, "Updating LoadBalancer ${lb.displayName} with backendSet ${description.backendSetName}"
      List<String> privateIps = []

      if (!description.placements) {
        // wait for instances to go into running state
        ServerGroup sgView
        long finishBy = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)
        boolean allUp = false
        while (!allUp && System.currentTimeMillis() < finishBy) {
          sgView = clusterProvider.getServerGroup(sg.credentials.name, sg.region, sg.name)
          if (sgView && (sgView.instanceCounts.up == sgView.instanceCounts.total)) {
            task.updateStatus BASE_PHASE, "All instances are Up"
            allUp = true
            break
          }
          task.updateStatus BASE_PHASE, "Waiting for server group instances to get to Up state"
          Thread.sleep(5000)
        }
        if (!allUp) {
          task.updateStatus(BASE_PHASE, "Timed out waiting for server group instances to get to Up state")
          task.fail()
          return
        }

        // get their ip addresses
        task.updateStatus BASE_PHASE, "Looking up instance IP addresses"
        sg.instances.each { instance ->
          def vnicAttachRs = description.credentials.computeClient.listVnicAttachments(ListVnicAttachmentsRequest.builder()
            .compartmentId(description.credentials.compartmentId)
            .instanceId(instance.id)
            .build())
          vnicAttachRs.items.each { vnicAttach ->
            def vnic = description.credentials.networkClient.getVnic(GetVnicRequest.builder()
              .vnicId(vnicAttach.vnicId).build()).vnic
            if (vnic.privateIp) {
              instance.privateIp = vnic.privateIp
              privateIps << vnic.privateIp
            }
          }
        }
      } else {
        privateIps = sg.instances.collect { it.privateIp } as List
      }
      sg.backendSetName = description.backendSetName
      task.updateStatus BASE_PHASE, "Adding IP addresses ${privateIps} to ${description.backendSetName}"
      oracleServerGroupService.updateServerGroup(sg)
      // update listener and backendSet
      BackendSet defaultBackendSet = lb.backendSets.get(description.backendSetName)
      // new backends from the serverGroup
      List<BackendDetails> backends = privateIps.collect { ip ->
        BackendDetails.builder().ipAddress(ip).port(defaultBackendSet.healthChecker.port).build()
      }
      //merge with existing backendSet
      defaultBackendSet.backends.each { existingBackend ->
        backends << Details.of(existingBackend)
      }

      UpdateBackendSetDetails updateDetails = UpdateBackendSetDetails.builder()
        .policy(defaultBackendSet.policy)
        .healthChecker(Details.of(defaultBackendSet.healthChecker))
        .backends(backends).build()
      task.updateStatus BASE_PHASE, "Updating backendSet ${description.backendSetName}"
      UpdateBackendSetResponse updateRes = description.credentials.loadBalancerClient.updateBackendSet(
        UpdateBackendSetRequest.builder().loadBalancerId(description.loadBalancerId)
        .backendSetName(description.backendSetName).updateBackendSetDetails(updateDetails).build())

      // wait for backend set to be created
      OracleWorkRequestPoller.poll(updateRes.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
    }
    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = ["$region:$serverGroupName".toString()]
    deploymentResult.serverGroupNameByRegion[region] = serverGroupName
    return deploymentResult
  }

  InstanceConfiguration instanceConfig(String serverGroup, BasicOracleDeployDescription desc) {
    ComputeManagementClient client = desc.credentials.computeManagementClient
    String compartmentId = desc.credentials.compartmentId
    InstanceConfigurationCreateVnicDetails vnicDetails =
        InstanceConfigurationCreateVnicDetails.builder().build()

    InstanceConfigurationInstanceSourceViaImageDetails sourceDetails =
        InstanceConfigurationInstanceSourceViaImageDetails.builder().imageId(desc.imageId).build()

    InstanceConfigurationLaunchInstanceDetails launchDetails =
        InstanceConfigurationLaunchInstanceDetails.builder().compartmentId(compartmentId)
            .displayName(serverGroup + "-instance")// instance display name
            .createVnicDetails(vnicDetails).shape("VM.Standard2.1").sourceDetails(sourceDetails)
            .build()

    ComputeInstanceDetails instanceDetails =
        ComputeInstanceDetails.builder().launchDetails(launchDetails)
            .secondaryVnics(Collections.EMPTY_LIST).blockVolumes(Collections.EMPTY_LIST).build();

    CreateInstanceConfigurationDetails configuDetails =
        CreateInstanceConfigurationDetails.builder().displayName(desc.qualifiedName() + "-config")
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
  InstancePool createInstancePool(String serverGroup, BasicOracleDeployDescription desc) {
    InstanceConfiguration instanceConfiguration = instanceConfig(serverGroup, desc)
//    String subnetId = desc.getSubnetId();
//    String[] availabilityDomains = desc.getAvailabilityDomain();

    ComputeManagementClient client = desc.credentials.computeManagementClient
    String compartmentId = desc.credentials.compartmentId

//    CreateInstancePoolPlacementConfigurationDetails placementDetails =
//        CreateInstancePoolPlacementConfigurationDetails.builder().primarySubnetId(subnetId).
//            .availabilityDomain(availabilityDomain).secondaryVnicSubnets(Collections.EMPTY_LIST)
//            .build();
//
//    List<CreateInstancePoolPlacementConfigurationDetails> placementConfigurationList =
//        new ArrayList<>();
//    placementConfigurationList.add(placementDetails);

    CreateInstancePoolDetails createInstancePoolDetails = CreateInstancePoolDetails.builder()
        // instancePool dispalyName
      .displayName(serverGroup)
      .compartmentId(compartmentId).instanceConfigurationId(instanceConfiguration.getId())
      .size(desc.targetSize()).placementConfigurations(desc.getPlacements()).build()

    CreateInstancePoolRequest request = CreateInstancePoolRequest.builder()
      .createInstancePoolDetails(createInstancePoolDetails).build()

    CreateInstancePoolResponse response = client.createInstancePool(request)
    return response.getInstancePool()
  }
}

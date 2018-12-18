/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.op

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DestroyOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.core.ComputeManagementClient
import com.oracle.bmc.core.requests.DeleteInstanceConfigurationRequest
import com.oracle.bmc.core.requests.ListInstancePoolsRequest
import com.oracle.bmc.core.requests.TerminateInstancePoolRequest
import com.oracle.bmc.core.responses.DeleteInstanceConfigurationResponse
import com.oracle.bmc.core.responses.ListInstancePoolsResponse
import com.oracle.bmc.core.responses.TerminateInstancePoolResponse
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.model.BmcException
import org.springframework.beans.factory.annotation.Autowired

class DestroyOracleServerGroupAtomicOperation implements AtomicOperation<Void> {

  private final DestroyOracleServerGroupDescription description

  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  OracleServerGroupService oracleServerGroupService

  DestroyOracleServerGroupAtomicOperation(DestroyOracleServerGroupDescription description) {
    this.description = description
  }

  Void terminateInstancePool(List priorOutputs, OracleServerGroup serverGroup) {
    ComputeManagementClient client = description.credentials.computeManagementClient
    if (serverGroup.instancePoolId) {
      TerminateInstancePoolRequest termReq =
          TerminateInstancePoolRequest.builder().instancePoolId(serverGroup.instancePoolId).build()
      TerminateInstancePoolResponse termRes = client.terminateInstancePool(termReq)
      task.updateStatus(BASE_PHASE, "TerminateInstancePoolResponse " + termRes.getOpcRequestId())
    } else {
      ListInstancePoolsRequest listReq = ListInstancePoolsRequest.builder()
          .compartmentId(description.credentials.compartmentId).build(); //TODO: displayName(desc.getServerGroupName())
      String sgName = description.serverGroupName
      ListInstancePoolsResponse listRes = client.listInstancePools(listReq)
      listRes.items.each { instancePool ->
        if (sgName != null && sgName.equals(instancePool.displayName)) {
          task.updateStatus(BASE_PHASE, "TerminateInstancePoolRequest " + instancePool.displayName)
          TerminateInstancePoolResponse termRes = client.terminateInstancePool(
            TerminateInstancePoolRequest.builder().instancePoolId(instancePool.id).build())
          task.updateStatus(BASE_PHASE, "TerminateInstancePoolResponse " + termRes.getOpcRequestId())
        }
      }
    }
    if (serverGroup.instanceConfigurationId) {
      DeleteInstanceConfigurationResponse res = client.deleteInstanceConfiguration(DeleteInstanceConfigurationRequest.builder()
        .instanceConfigurationId(serverGroup.instanceConfigurationId).build())
      task.updateStatus(BASE_PHASE, "DeleteInstanceConfigurationResponse " + res.getOpcRequestId())
    }
  }

  @Override
  Void operate(List priorOutputs) {
    def app = Names.parseName(description.serverGroupName).app
    task.updateStatus BASE_PHASE, "Destroying serverGroup ${description.serverGroupName} of ${app}"
    OracleServerGroup serverGroup = oracleServerGroupService.getServerGroup(description.credentials, app, description.serverGroupName)
    LoadBalancer loadBalancer = null
    try {
      loadBalancer = serverGroup?.loadBalancerId? description.credentials.loadBalancerClient.getLoadBalancer(
        GetLoadBalancerRequest.builder().loadBalancerId(serverGroup.loadBalancerId).build())?.getLoadBalancer() : null
    } catch(BmcException e) {
        if (e.statusCode == 404) {
          task.updateStatus BASE_PHASE, "LoadBalancer ${serverGroup.loadBalancerId} did not exist...continuing"
        } else {
          throw e
        }
    }
    task.updateStatus BASE_PHASE, "removing instances from LoadBalancer(${loadBalancer?.displayName}) BackendSet(${serverGroup?.backendSetName})"
    if (loadBalancer) {
      Set<OracleServerGroup> toGo = serverGroup.instances.collect{it} as Set
      oracleServerGroupService.updateLoadBalancer(task, serverGroup, toGo, [] as Set)
    }

    task.updateStatus BASE_PHASE, "Destroying server group: " + description.serverGroupName
    if (serverGroup?.instancePoolId) {
      terminateInstancePool(priorOutputs, serverGroup)
      oracleServerGroupService.deleteServerGroup(serverGroup)
    } else {
      oracleServerGroupService.destroyServerGroup(task, description.credentials, description.serverGroupName)
    }

    task.updateStatus BASE_PHASE, "Completed server group destruction"
    return null
  }
}

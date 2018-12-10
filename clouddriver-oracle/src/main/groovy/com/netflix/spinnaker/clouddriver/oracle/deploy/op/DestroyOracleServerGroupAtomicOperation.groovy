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
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DestroyOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.model.Details
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.core.ComputeManagementClient
import com.oracle.bmc.core.requests.ListInstancePoolsRequest
import com.oracle.bmc.core.requests.TerminateInstancePoolRequest
import com.oracle.bmc.core.responses.ListInstancePoolsResponse
import com.oracle.bmc.core.responses.TerminateInstancePoolResponse
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
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

  Void terminateInstancePool(List priorOutputs) {
    ComputeManagementClient client = description.credentials.computeManagementClient
    if (description.instancePoolId) {
      TerminateInstancePoolRequest termReq =
          TerminateInstancePoolRequest.builder().instancePoolId(description.instancePoolId).build()
      TerminateInstancePoolResponse termRes = client.terminateInstancePool(termReq)
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
    return null;
  }

  @Override
  Void operate(List priorOutputs) {
    def app = Names.parseName(description.serverGroupName).app
    task.updateStatus BASE_PHASE, "Destroying server group backend set ${description.serverGroupName} of ${app}"
    def serverGroup = oracleServerGroupService.getServerGroup(description.credentials, app, description.serverGroupName)
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
    if (loadBalancer) {
      Set<String> toGo = serverGroup.instances.collect {it.privateIp} as Set
      try {
        BackendSet backendSet = serverGroup.backendSetName? loadBalancer.backendSets.get(serverGroup.backendSetName) : null
        if (backendSet == null && loadBalancer.backendSets.size() == 1) {
          backendSet = loadBalancer.backendSets.values().first();
        }
        if (backendSet) {
          // remove serverGroup instances/IPs from the backendSet
          def backends = backendSet.backends.findAll { !toGo.contains(it.ipAddress) } .collect { Details.of(it) }
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
          def updateRes = description.credentials.loadBalancerClient.updateBackendSet(updateBackendSet)
          OracleWorkRequestPoller.poll(updateRes.opcWorkRequestId, BASE_PHASE, task, description.credentials.loadBalancerClient)
        }
      } catch (BmcException e) {
        if (e.statusCode == 404) {
          task.updateStatus BASE_PHASE, "Backend set did not exist...continuing"
        } else {
          throw e
        }
      }
    }

    task.updateStatus BASE_PHASE, "Destroying server group: " + description.serverGroupName
    if (serverGroup?.launchConfig?.placements && serverGroup?.launchConfig?.placements.size() > 0) {
      terminateInstancePool(priorOutputs)
      oracleServerGroupService.deleteServerGroup(serverGroup)
    } else {
      oracleServerGroupService.destroyServerGroup(task, description.credentials, description.serverGroupName)
    }

    task.updateStatus BASE_PHASE, "Completed server group destruction"
    return null
  }
}

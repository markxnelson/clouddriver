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
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.ResizeOracleServerGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.core.requests.GetVnicRequest
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Autowired

class ResizeOracleServerGroupAtomicOperation implements AtomicOperation<Void> {

  private final ResizeOracleServerGroupDescription description

  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  OracleServerGroupService oracleServerGroupService

  @Autowired
  OracleClusterProvider clusterProvider

  ResizeOracleServerGroupAtomicOperation(ResizeOracleServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def app = Names.parseName(description.serverGroupName).app
    task.updateStatus BASE_PHASE, "Resizing server group: " + description.serverGroupName
    def serverGroup = oracleServerGroupService.getServerGroup(description.credentials, app, description.serverGroupName)
    if (serverGroup == null) {
      task.updateStatus BASE_PHASE, "serverGroup " + description.serverGroupName + " not found"
      return
    }
    int targetSize = description.targetSize()

    System.out.println( '~~~  ????   targetSize:' + targetSize + ' insSize:' +  serverGroup?.instances?.size())

    if (targetSize == serverGroup?.instances?.size()) {
      task.updateStatus BASE_PHASE, description.serverGroupName + " is already running the desired number of instances"
      return
    }
    Set<OracleInstance> oldInstances = serverGroup.instances?.collect{it}?: [] as Set
    Set<String> oldGroup = oldInstances.collect{it.privateIp} as Set<String>

    oracleServerGroupService.resizeServerGroup(task, description.credentials, description.serverGroupName, targetSize)

    serverGroup = oracleServerGroupService.getServerGroup(description.credentials, app, description.serverGroupName)

    if (serverGroup.loadBalancerId) {
      if (serverGroup.instancePoolId != null) {
        serverGroup.instances = oracleServerGroupService.poll(task, serverGroup)
      } else {
        // wait for instances to go into running state
        ServerGroup sgView
        long finishBy = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)
        boolean allUp = false
        while (!allUp && System.currentTimeMillis() < finishBy) {
          sgView = clusterProvider.getServerGroup(serverGroup.credentials.name, serverGroup.region, serverGroup.name)
          if (sgView && (sgView.instanceCounts.up == sgView.instanceCounts.total) && (sgView.instanceCounts.total == targetSize)) {
            task.updateStatus BASE_PHASE, "All instances are Up"
            allUp = true
            break
          }
          task.updateStatus BASE_PHASE, "Waiting for serverGroup instances(${sgView?.instanceCounts?.up}) to match target size(${targetSize})"
          Thread.sleep(5000)
        }
        if (!allUp) {
          task.updateStatus(BASE_PHASE, "Timed out waiting for server group resize")
          task.fail()
          return
        }

        // get their ip addresses
        task.updateStatus BASE_PHASE, "Looking up instance IP addresses"
        List<String> newGroup = []
        serverGroup.instances.each { instance ->
          if (!instance.privateIp) {
            def vnicAttachRs = description.credentials.computeClient.listVnicAttachments(ListVnicAttachmentsRequest.builder()
              .compartmentId(description.credentials.compartmentId)
              .instanceId(((OracleInstance) instance).id)
              .build())
            vnicAttachRs.items.each { vnicAttach ->
              def vnic = description.credentials.networkClient.getVnic(GetVnicRequest.builder()
                .vnicId(vnicAttach.vnicId).build()).vnic
              instance.privateIp = vnic.privateIp
            }
          }
          newGroup << instance.privateIp
        }
        //update serverGroup with IPs
        oracleServerGroupService.updateServerGroup(serverGroup)
        oracleServerGroupService.updateLoadBalancer(task, serverGroup, oldInstances, serverGroup.instances)
      }
    }
    task.updateStatus BASE_PHASE, "Completed server group resize"
    return null
  }
}

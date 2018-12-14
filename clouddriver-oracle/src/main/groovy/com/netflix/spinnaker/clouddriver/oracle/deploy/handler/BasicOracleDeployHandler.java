/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.handler;

import static java.util.Collections.emptySet;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.BasicOracleDeployDescription;
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup;
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider;
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService;
import com.oracle.bmc.core.model.Vnic;
import com.oracle.bmc.core.requests.GetVnicRequest;
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest;
import com.oracle.bmc.core.responses.ListVnicAttachmentsResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BasicOracleDeployHandler implements DeployHandler<BasicOracleDeployDescription> {

  private static final String BASE_PHASE = "DEPLOY";

  @Autowired
  OracleServerGroupService oracleServerGroupService;

  @Autowired
  OracleClusterProvider clusterProvider;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public boolean handles(DeployDescription description) {
    return (description instanceof BasicOracleDeployDescription);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public DeploymentResult handle(BasicOracleDeployDescription description, List priorOutputs) {
    String region = description.getRegion();
    OracleServerGroupNameResolver serverGroupNameResolver = new OracleServerGroupNameResolver(oracleServerGroupService, description.getCredentials(), region);
    String clusterName = serverGroupNameResolver.qualifiedName(description.getApplication(), description.getStack(), description.getFreeFormDetails());
    Task task = getTask();
    task.updateStatus(BASE_PHASE, "Initializing creation of server group for cluster " + clusterName + " ...");
    task.updateStatus(BASE_PHASE, "Looking up next sequence...");
    String serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.getApplication(), description.getStack(), description.getFreeFormDetails(), false);
    task.updateStatus(BASE_PHASE, "Produced server group name: " + serverGroupName);

    Map<String, Object> launchConfig = new HashMap<>();
    launchConfig.put("availabilityDomain", description.getAvailabilityDomain());
    launchConfig.put("compartmentId"     , description.getCredentials().getCompartmentId());
    launchConfig.put("imageId"           , description.getImageId());
    launchConfig.put("shape"             , description.getShape());
    launchConfig.put("vpcId"             , description.getVpcId());
    launchConfig.put("subnetId"          , description.getSubnetId());
    launchConfig.put("sshAuthorizedKeys" , description.getSshAuthorizedKeys());
    launchConfig.put("createdTime"       , System.currentTimeMillis());

    int targetSize = description.targetSize();
task.updateStatus(BASE_PHASE, "JAVA JAVA JAVA JAVA JAVA Composing server group " + serverGroupName + " with " + targetSize + " instance(s)");
    task.updateStatus(BASE_PHASE, "Composing server group " + serverGroupName + " with " + targetSize + " instance(s)");

    OracleServerGroup sg = new OracleServerGroup (
       serverGroupName,
       description.getRegion(),
       description.getAvailabilityDomain(),
       launchConfig,
       targetSize,
       description.getCredentials(),
       description.getLoadBalancerId(),
       description.getBackendSetName(),
       description.getPlacements()
    );

    oracleServerGroupService.createServerGroup(task, sg);
    task.updateStatus(BASE_PHASE, "Done creating server group " + serverGroupName);

    if (description.getLoadBalancerId() != null) {
      if (description.getPlacements() == null || description.getPlacements().size() == 0) { //for non-instancePool
        // wait for instances to go into running state
        ServerGroup sgView = null;
        long finishBy = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30);
        boolean allUp = false;
        while (!allUp && System.currentTimeMillis() < finishBy) {
          sgView = clusterProvider.getServerGroup(sg.getCredentials().getName(), sg.getRegion(), sg.getName());
          if ((sgView != null) && (sgView.getInstanceCounts().getUp() == sgView.getInstanceCounts().getTotal())) {
            task.updateStatus(BASE_PHASE, "All instances are Up");
            allUp = true;
            break;
          }
          int currentSize = (sgView != null && sgView.getInstanceCounts() != null)? sgView.getInstanceCounts().getUp() : 0;
          int totalSize = (sgView != null && sgView.getInstanceCounts() != null)? sgView.getInstanceCounts().getTotal() : targetSize;
          task.updateStatus(BASE_PHASE, "Waiting for serverGroup instances(" + currentSize + ") to get to Up(" + totalSize + ") state");
          try {
            Thread.sleep(5000);
          } catch (InterruptedException e) {
          }
        }
        if (!allUp) {
          task.updateStatus(BASE_PHASE, "Timed out waiting for server group instances to get to Up state");
          task.fail();
          return null;
        }

        // get their ip addresses
        task.updateStatus(BASE_PHASE, "Looking up instance IP addresses");
        sg.getInstances().forEach( instance -> {
          instance.getId();
          ListVnicAttachmentsResponse vnicAttachRs = description.getCredentials().getComputeClient().listVnicAttachments(ListVnicAttachmentsRequest.builder()
            .compartmentId(description.getCredentials().getCompartmentId())
            .instanceId(instance.getId())
            .build());
          vnicAttachRs.getItems().forEach(  vnicAttach -> {
            Vnic vnic = description.getCredentials().getNetworkClient().getVnic(GetVnicRequest.builder()
              .vnicId(vnicAttach.getVnicId()).build()).getVnic();
            if (vnic.getPrivateIp() != null) {
              instance.setPrivateIp(vnic.getPrivateIp());
            }
          });
        });
        oracleServerGroupService.updateServerGroup(sg);
        oracleServerGroupService.updateLoadBalancer(task, sg, emptySet(), sg.getInstances());
      } else {
        oracleServerGroupService.poll(task, sg);
      }
    }
    DeploymentResult deploymentResult = new DeploymentResult();
    List<String> sgNames = new ArrayList<>();
    sgNames.add(region + ":" + serverGroupName);
    deploymentResult.setServerGroupNames(sgNames);
    deploymentResult.getServerGroupNameByRegion().put(region, serverGroupName);
    return deploymentResult;
  }
}

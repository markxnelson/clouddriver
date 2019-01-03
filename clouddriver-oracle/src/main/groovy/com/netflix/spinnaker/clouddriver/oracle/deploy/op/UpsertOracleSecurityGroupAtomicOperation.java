/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.op;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertOracleSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.oracle.bmc.core.model.CreateSecurityListDetails;
import com.oracle.bmc.core.model.EgressSecurityRule;
import com.oracle.bmc.core.model.IngressSecurityRule;
import com.oracle.bmc.core.model.UpdateSecurityListDetails;
import com.oracle.bmc.core.requests.CreateSecurityListRequest;
import com.oracle.bmc.core.requests.UpdateSecurityListRequest;
import groovy.util.logging.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class UpsertOracleSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private final String BASE_PHASE = "UPSERT_SECURITY_GROUP";
  private UpsertOracleSecurityGroupDescription description;

  public UpsertOracleSecurityGroupAtomicOperation(UpsertOracleSecurityGroupDescription description) {
    this.description = description;
  }

  protected static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public UpsertOracleSecurityGroupDescription getDescription() {
    return description;
  }

  public void setDescription(UpsertOracleSecurityGroupDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    if (description.getSecurityGroupId() != null && description.getSecurityGroupId().trim().length() > 0) {
      update();
    } else {
      create();
    }
    return null;
  }

  private void create() {
    getTask().updateStatus(BASE_PHASE, "Creating security group " + getDescription().getSecurityGroupName());

    CreateSecurityListDetails details = CreateSecurityListDetails.builder()
            .displayName(description.getSecurityGroupName())
            .compartmentId(description.getCredentials().getCompartmentId())
            .vcnId(description.getVcnId())
            .ingressSecurityRules(buildIngressRules())
            .egressSecurityRules(buildEgressRules())
            .build();
    CreateSecurityListRequest request = CreateSecurityListRequest.builder().createSecurityListDetails(details).build();
    description.getCredentials().getNetworkClient().createSecurityList(request);

    getTask().updateStatus(BASE_PHASE, "Finished creating security group " + getDescription().getSecurityGroupName());
  }

  private void update() {
    getTask().updateStatus(BASE_PHASE, "Updating security group " + getDescription().getSecurityGroupName());

    UpdateSecurityListDetails details = UpdateSecurityListDetails.builder()
            .displayName(description.getSecurityGroupName())
            .ingressSecurityRules(buildIngressRules())
            .egressSecurityRules(buildEgressRules())
            .build();
    UpdateSecurityListRequest request = UpdateSecurityListRequest.builder().updateSecurityListDetails(details).build();
    description.getCredentials().getNetworkClient().updateSecurityList(request);

    getTask().updateStatus(BASE_PHASE, "Finished updating security group " + getDescription().getSecurityGroupName());
  }

  private List<IngressSecurityRule> buildIngressRules() {
    if (description.getInboundRules() != null && description.getInboundRules().size() > 0) {
      List<IngressSecurityRule> rules = description.getInboundRules().stream()
              .map(it -> it.transformIngressRule()).collect(Collectors.toList());
      return rules;
    }
    return null;
  }

  private List<EgressSecurityRule> buildEgressRules() {
    if (description.getOutboundRules() != null && description.getOutboundRules().size() > 0) {
      List<EgressSecurityRule> rules = description.getOutboundRules().stream()
              .map(it -> it.transformEgressRule()).collect(Collectors.toList());
      return rules;
    }
    return null;
  }
}

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
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DeleteOracleSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.oracle.bmc.core.requests.DeleteSecurityListRequest;

import java.util.List;

/**
 * Deletes an Oracle security group.
 * <p>
 * Delete will fail if the security group is associated to an instance.
 */
public class DeleteOracleSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private final String BASE_PHASE = "DELETE_SECURITY_GROUP";

  private DeleteOracleSecurityGroupDescription description;

  public DeleteOracleSecurityGroupAtomicOperation(DeleteOracleSecurityGroupDescription description) {
    this.description = description;
  }

  protected static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public DeleteOracleSecurityGroupDescription getDescription() {
    return description;
  }

  public void setDescription(DeleteOracleSecurityGroupDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Deleting security group " + getDescription().getSecurityGroupId());

    DeleteSecurityListRequest request = DeleteSecurityListRequest.builder().securityListId(description.getSecurityGroupId()).build();
    description.getCredentials().getNetworkClient().deleteSecurityList(request);

    getTask().updateStatus(BASE_PHASE, "Finished deleting security group " + getDescription().getSecurityGroupId());
    return null;
  }
}

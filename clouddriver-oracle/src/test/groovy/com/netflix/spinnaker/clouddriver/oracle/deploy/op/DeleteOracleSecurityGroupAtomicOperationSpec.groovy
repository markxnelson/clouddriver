/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.op

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DeleteOracleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.core.requests.DeleteSecurityListRequest
import spock.lang.Specification

class DeleteOracleSecurityGroupAtomicOperationSpec extends Specification {

  def "Triggers destroying of a security group"() {
    setup:
    def id = "testId"
    def deleteDesc = new DeleteOracleSecurityGroupDescription(securityGroupId: id)
    def mockNetworkClient = Mock(VirtualNetworkClient)
    def mockCredentials = Mock(OracleNamedAccountCredentials)
    mockCredentials.networkClient >> mockNetworkClient
    deleteDesc.credentials = mockCredentials
    DeleteOracleSecurityGroupAtomicOperation op = new DeleteOracleSecurityGroupAtomicOperation(deleteDesc)

    TaskRepository.threadLocalTask.set(Mock(Task))
    GroovySpy(OracleWorkRequestPoller, global: true)

    when:
    op.operate(null)

    then:
    1 * mockNetworkClient.deleteSecurityList(_) >> { args ->
      DeleteSecurityListRequest req = (DeleteSecurityListRequest) args[0]
      assert req.securityListId == id
    }
  }
}

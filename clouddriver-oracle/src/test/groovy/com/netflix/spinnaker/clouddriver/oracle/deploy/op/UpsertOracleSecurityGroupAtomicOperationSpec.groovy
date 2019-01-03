/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.op

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oracle.ResourceUtil
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.converter.UpsertOracleSecurityGroupAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.core.requests.CreateSecurityListRequest
import com.oracle.bmc.core.requests.UpdateSecurityListRequest
import spock.lang.Shared
import spock.lang.Specification

class UpsertOracleSecurityGroupAtomicOperationSpec extends Specification {

  @Shared
  UpsertOracleSecurityGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertOracleSecurityGroupAtomicOperationConverter(objectMapper: new ObjectMapper())
  }

  def "Create a security group"() {
    setup:
    def input = ResourceUtil.read("upsertSecurityGroup_create.json")
    def mockNetworkClient = Mock(VirtualNetworkClient)
    def mockCredentials = Mock(OracleNamedAccountCredentials)
    mockCredentials.networkClient >> mockNetworkClient
    def mockAccountCredentialsProvider = Mock(AccountCredentialsProvider)
    mockAccountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = mockAccountCredentialsProvider
    def op = converter.convertOperation(input.upsertSecurityGroup)

    TaskRepository.threadLocalTask.set(Mock(Task))
    GroovySpy(OracleWorkRequestPoller, global: true)

    when:
    op.operate(null)

    then:
    1 * mockNetworkClient.createSecurityList(_) >> { args ->
      CreateSecurityListRequest req = (CreateSecurityListRequest) args[0]
      assert req.createSecurityListDetails.displayName == "testName"
      assert req.createSecurityListDetails.vcnId == "ocid1.vcn.oc1.iad.aaaaaaaau6n2ekz5zisihitvjfijzad7vb4e22xkswo4ctm5ndzxmj4d4n3q"
    }
  }

  def "Update a security group"() {
    setup:
    def input = ResourceUtil.read("upsertSecurityGroup_update.json")
    def mockNetworkClient = Mock(VirtualNetworkClient)
    def mockCredentials = Mock(OracleNamedAccountCredentials)
    mockCredentials.networkClient >> mockNetworkClient
    def mockAccountCredentialsProvider = Mock(AccountCredentialsProvider)
    mockAccountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = mockAccountCredentialsProvider
    def op = converter.convertOperation(input.upsertSecurityGroup)

    TaskRepository.threadLocalTask.set(Mock(Task))
    GroovySpy(OracleWorkRequestPoller, global: true)

    when:
    op.operate(null)

    then:
    1 * mockNetworkClient.updateSecurityList(_) >> { args ->
      UpdateSecurityListRequest req = (UpdateSecurityListRequest) args[0]
      assert req.updateSecurityListDetails.displayName == "newTestName"
    }
  }
}

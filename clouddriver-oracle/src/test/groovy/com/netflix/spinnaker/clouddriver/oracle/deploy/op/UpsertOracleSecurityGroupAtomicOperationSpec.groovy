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
import com.oracle.bmc.core.model.SecurityList
import com.oracle.bmc.core.model.Subnet
import com.oracle.bmc.core.requests.CreateSecurityListRequest
import com.oracle.bmc.core.requests.GetSubnetRequest
import com.oracle.bmc.core.requests.UpdateSecurityListRequest
import com.oracle.bmc.core.requests.UpdateSubnetRequest
import com.oracle.bmc.core.responses.CreateSecurityListResponse
import com.oracle.bmc.core.responses.GetSubnetResponse
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
    def testSecurityListId = "ocid1.securitylist.oc1.iad.aaaaaaaap2uliq5zqtdcp2cryvsadovnozflpyevlig7skpmsdwfrhzz2t7q"
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
      assert req.createSecurityListDetails.displayName == "testApp-dev-fw"
      assert req.createSecurityListDetails.vcnId == "ocid1.vcn.oc1.iad.aaaaaaaau6n2ekz5zisihitvjfijzad7vb4e22xkswo4ctm5ndzxmj4d4n3q"
      CreateSecurityListResponse.builder().securityList(SecurityList.builder().id(testSecurityListId).build()).build()
    }
    2 * mockNetworkClient.getSubnet(_) >> { args ->
      GetSubnetRequest req = (GetSubnetRequest) args[0]
      assert req.subnetId == "ocid1.subnet.oc1.iad.aaaaaaaaqsodjryopkdbhw7revjt4imoxarqxishrff7xfvxuvfmn2mbs6ba" || "ocid1.subnet.oc1.iad.aaaaaaaaqsodjryopkdbhw7revjt4imoxarqxishrff7xfvxuvfmn2mbs6bb"
      GetSubnetResponse.builder().subnet(Subnet.builder().build()).build()
    }
    2 * mockNetworkClient.updateSubnet(_) >> { args ->
      UpdateSubnetRequest req = (UpdateSubnetRequest) args[0]
      assert req.subnetId == "ocid1.subnet.oc1.iad.aaaaaaaaqsodjryopkdbhw7revjt4imoxarqxishrff7xfvxuvfmn2mbs6ba" || "ocid1.subnet.oc1.iad.aaaaaaaaqsodjryopkdbhw7revjt4imoxarqxishrff7xfvxuvfmn2mbs6bb"
      assert req.updateSubnetDetails.securityListIds == [testSecurityListId]
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
      assert req.updateSecurityListDetails.displayName == "testApp-dev-fw"
      assert req.securityListId == "ocid1.securitylist.oc1.iad.aaaaaaaamk3at3prhgwztqsz4pulmf72wffiftybl6rtrvxv4ebpvqylkuiq"
    }
  }
}

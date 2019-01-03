/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.converter

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.oracle.ResourceUtil
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertOracleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.UpsertOracleSecurityGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.oracle.model.OracleSecurityRule
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class UpsertOracleSecurityGroupAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  UpsertOracleSecurityGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertOracleSecurityGroupAtomicOperationConverter(objectMapper: new ObjectMapper())
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(OracleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  def "return correct description and operation"() {
    setup:
    def input = ResourceUtil.read(descFile)

    when:
    def operation = converter.convertOperation(input.upsertSecurityGroup)

    then:
    operation instanceof UpsertOracleSecurityGroupAtomicOperation
    operation.description instanceof UpsertOracleSecurityGroupDescription
    operation.description.securityGroupId == expectedId
    operation.description.securityGroupName == expectedName
    operation.description.vcnId == expectedVcn
    operation.description.inboundRules[0] instanceof OracleSecurityRule
    operation.description.inboundRules[0].stateless == false
    operation.description.inboundRules[0].protocol == "TCP"
    operation.description.inboundRules[0].type == "CIDR_BLOCK"
    operation.description.inboundRules[0].typeDetail == "0.0.0.0/0"
    operation.description.inboundRules[0].sourcePortRange.startPort == 80
    operation.description.inboundRules[0].sourcePortRange.endPort == 81
    operation.description.inboundRules[0].destinationPortRange.startPort == 8080
    operation.description.inboundRules[0].destinationPortRange.endPort == 8081
    operation.description.outboundRules[0] instanceof OracleSecurityRule
    operation.description.outboundRules[0].stateless == true
    operation.description.outboundRules[0].protocol == "UDP"
    operation.description.outboundRules[0].type == "SERVICE_CIDR_BLOCK"
    operation.description.outboundRules[0].typeDetail == "testService"
    operation.description.outboundRules[0].sourcePortRange.startPort == 90
    operation.description.outboundRules[0].sourcePortRange.endPort == 91
    operation.description.outboundRules[0].destinationPortRange.startPort == 9080
    operation.description.outboundRules[0].destinationPortRange.endPort == 9081

    where:
    descFile | expectedId | expectedName | expectedVcn
    "upsertSecurityGroup_create.json" | null | "testName" | "ocid1.vcn.oc1.iad.aaaaaaaau6n2ekz5zisihitvjfijzad7vb4e22xkswo4ctm5ndzxmj4d4n3q"
    "upsertSecurityGroup_update.json" | "ocid1.securitylist.oc1.iad.aaaaaaaamk3at3prhgwztqsz4pulmf72wffiftybl6rtrvxv4ebpvqylkuiq" | "newTestName" | null
  }
}

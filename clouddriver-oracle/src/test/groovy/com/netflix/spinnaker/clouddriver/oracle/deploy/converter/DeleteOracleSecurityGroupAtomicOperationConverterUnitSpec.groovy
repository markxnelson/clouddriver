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
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DeleteOracleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.DeleteOracleSecurityGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class DeleteOracleSecurityGroupAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  DeleteOracleSecurityGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeleteOracleSecurityGroupAtomicOperationConverter(objectMapper: new ObjectMapper())
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(OracleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  def "return correct description and operation"() {
    setup:
    def id = "testId"
    def input = [securityGroupId: id]

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DeleteOracleSecurityGroupAtomicOperation
    operation.description instanceof DeleteOracleSecurityGroupDescription
    operation.description.getSecurityGroupId() == id
  }
}

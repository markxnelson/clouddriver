/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.validator

import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertOracleSecurityGroupDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertSecurityGroupDescriptionValidatorSpec extends Specification {

  public static final String CONTEXT = "upsertOracleSecurityGroupDescriptionValidator"

  @Shared
  UpsertOracleSecurityGroupDescriptionValidator validator

  def setupSpec() {
    validator = new UpsertOracleSecurityGroupDescriptionValidator()
  }

  def "invalid description empty name"() {
    setup:
    def desc = new UpsertOracleSecurityGroupDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    1 * errors.rejectValue('securityGroupName', CONTEXT + '.securityGroupName.empty')
  }

  def "invalid description empty vcnId"() {
    setup:
    def desc = new UpsertOracleSecurityGroupDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    1 * errors.rejectValue('vcnId', CONTEXT + '.vcnId.empty')
  }

  def "invalid description notOCID vcnId"() {
    setup:
    def desc = new UpsertOracleSecurityGroupDescription(securityGroupName: "testName", vcnId: "testVcnId")
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    1 * errors.rejectValue('vcnId', CONTEXT + '.vcnId.notOCID')
  }

  void "valid description passes validation"() {
    setup:
    def desc = new UpsertOracleSecurityGroupDescription(securityGroupName: "testName", vcnId: "ocid1.vcn.oc1.iad.aaaaaaaau6n2ekz5zisihitvjfijzad7vb4e22xkswo4ctm5ndzxmj4d4n3q")
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    0 * errors._
  }
}

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

  def "invalid description empty vcnId"() {
    setup:
    def desc = new UpsertOracleSecurityGroupDescription(application: "testApp")
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    1 * errors.rejectValue('vpcId', CONTEXT + '.vpcId.empty')
  }

  def "invalid description notOCID vcnId"() {
    setup:
    def desc = new UpsertOracleSecurityGroupDescription(application: "testApp", vpcId: "testVcnId")
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    1 * errors.rejectValue('vpcId', CONTEXT + '.vpcId.notOCID')
  }

  def "invalid description notOCID securityGroupId"() {
    setup:
    def desc = new UpsertOracleSecurityGroupDescription(application: "testApp", securityGroupId: "testSecurityGroupId")
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    1 * errors.rejectValue('securityGroupId', CONTEXT + '.securityGroupId.notOCID')
  }

  void "valid description passes create validation"() {
    setup:
    def desc = new UpsertOracleSecurityGroupDescription(application: "testApp", vpcId: "ocid1.vcn.oc1.iad.aaaaaaaau6n2ekz5zisihitvjfijzad7vb4e22xkswo4ctm5ndzxmj4d4n3q")
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    0 * errors._
  }

  void "valid description passes update validation"() {
    setup:
    def desc = new UpsertOracleSecurityGroupDescription(application: "testApp", securityGroupId: "ocid1.securitylist.oc1.iad.aaaaaaaamk3at3prhgwztqsz4pulmf72wffiftybl6rtrvxv4ebpvqylkuiq")
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    0 * errors._
  }
}

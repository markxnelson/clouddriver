/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.validator

import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DeleteOracleSecurityGroupDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class DeleteOracleSecurityGroupDescriptionValidatorSpec extends Specification {

  public static final String CONTEXT = "deleteOracleSecurityGroupDescriptionValidator"

  @Shared
  DeleteOracleSecurityGroupDescriptionValidator validator

  void setupSpec() {
    validator = new DeleteOracleSecurityGroupDescriptionValidator()
  }

  void "invalid description securityGroupId empty"() {
    setup:
    def description = new DeleteOracleSecurityGroupDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("securityGroupId", "deleteOracleSecurityGroupDescriptionValidator.securityGroupId.empty")
  }

  void "invalid description securityGroupId not OCID "() {
    setup:
    def description = new DeleteOracleSecurityGroupDescription(securityGroupId: "testGroupId")

    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("securityGroupId", CONTEXT + ".securityGroupId.notOCID")
  }

  void "valid description passes validation"() {
    setup:
    def description = new DeleteOracleSecurityGroupDescription(securityGroupId: "ocid1.securitylist.oc1.iad.aaaaaaaaztzr54yk6yuqkei6rt42vi53eeqppk23dnhxbjctqnlrnu37fhda")

    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }
}

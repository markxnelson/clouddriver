/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.validator;

import com.netflix.spinnaker.clouddriver.oracle.OracleOperation;
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DeleteOracleSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import java.util.List;

/**
 * Validates the delete security group operation description.
 */
@OracleOperation(AtomicOperations.DELETE_SECURITY_GROUP)
@Component
public class DeleteOracleSecurityGroupDescriptionValidator extends StandardOracleAttributeValidator<DeleteOracleSecurityGroupDescription> {
  private final static String CONTEXT = "deleteOracleSecurityGroupDescriptionValidator";

  @Override
  public void validate(List priorDescriptions, DeleteOracleSecurityGroupDescription description, Errors errors) {
    context = CONTEXT;
    validateOCID(errors, description.getSecurityGroupId(), "securityGroupId");
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v1;
  }
}

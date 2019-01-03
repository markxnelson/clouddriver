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
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertOracleSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.oracle.model.OracleSecurityRule;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import java.util.List;

/**
 * Validates the upsert security group operation description.
 */
@OracleOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component
public class UpsertOracleSecurityGroupDescriptionValidator extends StandardOracleAttributeValidator<UpsertOracleSecurityGroupDescription> {
  private final static String CONTEXT = "upsertOracleSecurityGroupDescriptionValidator";

  @Override
  public void validate(List priorDescriptions, UpsertOracleSecurityGroupDescription description, Errors errors) {
    context = CONTEXT;
    if (description.getSecurityGroupId() != null && description.getSecurityGroupId().trim().length() > 0) {
      // update
      validateOCID(errors, description.getSecurityGroupId(), "securityGroupId");
    } else {
      // create
      validateNotEmptyString(errors, description.getSecurityGroupName(), "securityGroupName");
      validateOCID(errors, description.getVcnId(), "vcnId");
    }

    if (description.getInboundRules() != null) {
      for (OracleSecurityRule rule : description.getInboundRules()) {
        validateRule(errors, rule);
      }
    }
    if (description.getOutboundRules() != null) {
      for (OracleSecurityRule rule : description.getOutboundRules()) {
        validateRule(errors, rule);
      }
    }
  }

  private void validateRule(Errors errors, OracleSecurityRule rule) {
    validateNotEmptyString(errors, rule.getProtocol(), "protocol");
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v1;
  }
}

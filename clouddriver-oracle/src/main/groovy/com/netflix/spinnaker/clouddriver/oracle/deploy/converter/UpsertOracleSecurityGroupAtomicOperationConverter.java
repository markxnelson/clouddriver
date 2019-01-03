/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.converter;

import com.netflix.spinnaker.clouddriver.oracle.OracleOperation;
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertOracleSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.UpsertOracleSecurityGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import org.springframework.stereotype.Component;

import java.util.Map;

@OracleOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component
public class UpsertOracleSecurityGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @SuppressWarnings("rawtypes")
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new UpsertOracleSecurityGroupAtomicOperation(convertDescription(input));
  }

  @SuppressWarnings("rawtypes")
  @Override
  public UpsertOracleSecurityGroupDescription convertDescription(Map input) {
    return OracleAtomicOperationConverterHelper.convertDescription(input, this, UpsertOracleSecurityGroupDescription.class);
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v1;
  }
}

/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.description;

import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;

import java.util.Collection;
import java.util.Collections;

/**
 * Description for deleting security groups.
 */
public class DeleteOracleSecurityGroupDescription extends AbstractOracleCredentialsDescription implements ApplicationNameable {
  private String application;
  private String securityGroupId;

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public String getSecurityGroupId() {
    return securityGroupId;
  }

  public void setSecurityGroupId(String securityGroupId) {
    this.securityGroupId = securityGroupId;
  }

  @Override
  public Collection<String> getApplications() {
    return Collections.singleton(application);
  }
}

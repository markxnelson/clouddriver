/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.description;

import com.netflix.spinnaker.clouddriver.oracle.model.OracleSecurityRule;

import java.util.List;

/**
 * Description for creating security groups with rules
 */
public class UpsertOracleSecurityGroupDescription extends AbstractOracleCredentialsDescription {
  private String securityGroupId;
  private String securityGroupName;
  private String vcnId;
  private List<OracleSecurityRule> inboundRules;
  private List<OracleSecurityRule> outboundRules;

  public final String getSecurityGroupId() {
    return securityGroupId;
  }

  public void setSecurityGroupId(String securityGroupId) {
    this.securityGroupId = securityGroupId;
  }

  public final String getSecurityGroupName() {
    return securityGroupName;
  }

  public void setSecurityGroupName(String securityGroupName) {
    this.securityGroupName = securityGroupName;
  }

  public final String getVcnId() {
    return vcnId;
  }

  public void setVcnId(String vcnId) {
    this.vcnId = vcnId;
  }

  public final List<OracleSecurityRule> getInboundRules() {
    return inboundRules;
  }

  public void setInboundRules(List<OracleSecurityRule> inboundRules) {
    this.inboundRules = inboundRules;
  }

  public final List<OracleSecurityRule> getOutboundRules() {
    return outboundRules;
  }

  public void setOutboundRules(List<OracleSecurityRule> outboundRules) {
    this.outboundRules = outboundRules;
  }
}

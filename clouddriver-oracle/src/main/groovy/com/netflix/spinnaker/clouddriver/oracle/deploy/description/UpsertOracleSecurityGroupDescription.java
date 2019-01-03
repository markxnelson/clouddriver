/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.description;

import com.netflix.spinnaker.clouddriver.oracle.model.OracleEgressSecurityRule;
import com.netflix.spinnaker.clouddriver.oracle.model.OracleIngressSecurityRule;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Description for creating security groups with rules
 */
public class UpsertOracleSecurityGroupDescription extends AbstractOracleCredentialsDescription implements ApplicationNameable {
  private String application;
  private String stack;
  private String detail;
  private String securityGroupId;
  private String vpcId;
  private List<String> subnetIds;
  private List<OracleIngressSecurityRule> inboundRules;
  private List<OracleEgressSecurityRule> outboundRules;

  public String getSecurityGroupName() {
    String stack = this.stack == null ? "" : this.stack;
    if (this.detail != null && this.detail.trim().length() >0 ) {
      return this.application + "-" + stack + "-" + detail;
    }
    if (!stack.isEmpty()) {
      return this.application + "-" + stack;
    } else {
      return this.application;
    }
  }

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }


  public String getStack() {
    return stack;
  }

  public void setStack(String stack) {
    this.stack = stack;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public final String getSecurityGroupId() {
    return securityGroupId;
  }

  public void setSecurityGroupId(String securityGroupId) {
    this.securityGroupId = securityGroupId;
  }

  public final String getVpcId() {
    return vpcId;
  }

  public void setVpcId(String vpcId) {
    this.vpcId = vpcId;
  }

  public final List<OracleIngressSecurityRule> getInboundRules() {
    return inboundRules;
  }

  public void setInboundRules(List<OracleIngressSecurityRule> inboundRules) {
    this.inboundRules = inboundRules;
  }

  public final List<OracleEgressSecurityRule> getOutboundRules() {
    return outboundRules;
  }

  public void setOutboundRules(List<OracleEgressSecurityRule> outboundRules) {
    this.outboundRules = outboundRules;
  }

  public List<String> getSubnetIds() {
    return subnetIds;
  }

  public void setSubnetIds(List<String> subnetIds) {
    this.subnetIds = subnetIds;
  }

  @Override
  public Collection<String> getApplications() {
    return Collections.singleton(application);
  }
}

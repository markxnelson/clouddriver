/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.model

import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.oracle.bmc.core.model.EgressSecurityRule
import com.oracle.bmc.core.model.IngressSecurityRule
import com.oracle.bmc.core.model.PortRange
import com.oracle.bmc.core.model.TcpOptions
import spock.lang.Shared
import spock.lang.Specification

class OracleSecurityRuleSpec extends Specification {

  @Shared
  OracleSecurityRule oracleSecurityRule;

  def setupSpec() {
    oracleSecurityRule = new OracleSecurityRule(
      stateless: false,
      protocol: "TCP",
      type: "CIDR_BLOCK", // SERVICE_CIDR_BLOCK
      typeDetail: "testCIDRBlock",
      sourcePortRange: new Rule.PortRange(startPort: 80, endPort: 81),
      destinationPortRange: new Rule.PortRange(startPort: 8080, endPort: 8081)
    )
  }

  def "transform to IngressRule"() {
    setup:

    when:
    def ingress = oracleSecurityRule.transformIngressRule()

    then:
    ingress instanceof IngressSecurityRule
    ingress.isStateless == false
    ingress.protocol == "TCP"
    ingress.sourceType == IngressSecurityRule.SourceType.CidrBlock
    ingress.source == "testCIDRBlock"
    ingress.tcpOptions.sourcePortRange.min == 80
    ingress.tcpOptions.sourcePortRange.max == 81
    ingress.tcpOptions.destinationPortRange.min == 8080
    ingress.tcpOptions.destinationPortRange.max == 8081
  }

  def "transform to EgressRule"() {
    setup:

    when:
    def egress = oracleSecurityRule.transformEgressRule()

    then:
    egress instanceof EgressSecurityRule
    egress.isStateless == false
    egress.protocol == "TCP"
    egress.destinationType == EgressSecurityRule.DestinationType.CidrBlock
    egress.destination == "testCIDRBlock"
    egress.tcpOptions.sourcePortRange.min == 80
    egress.tcpOptions.sourcePortRange.max == 81
    egress.tcpOptions.destinationPortRange.min == 8080
    egress.tcpOptions.destinationPortRange.max == 8081
  }

  def "transform from IngressRule"() {
    setup:
    IngressSecurityRule ingress = IngressSecurityRule.builder()
      .isStateless(false)
      .protocol("TCP")
      .sourceType(IngressSecurityRule.SourceType.CidrBlock)
      .source("testCIDRBlock")
      .tcpOptions(TcpOptions.builder().
      sourcePortRange(PortRange.builder().min(80).max(81).build())
      .destinationPortRange(PortRange.builder().min(8080).max(8081).build()).build())
      .build()

    when:
    def rule = OracleSecurityRule.transform(ingress)

    then:
    rule instanceof OracleSecurityRule
    rule.stateless == false
    rule.protocol == "TCP"
    rule.type == IngressSecurityRule.SourceType.CidrBlock.getValue()
    rule.typeDetail == "testCIDRBlock"
    rule.sourcePortRange.startPort == 80
    rule.sourcePortRange.endPort == 81
    rule.destinationPortRange.startPort == 8080
    rule.destinationPortRange.endPort == 8081
  }

  def "transform from EgressRule"() {
    setup:
    EgressSecurityRule egress = EgressSecurityRule.builder()
      .isStateless(false)
      .protocol("TCP")
      .destinationType(EgressSecurityRule.DestinationType.CidrBlock)
      .destination("testCIDRBlock")
      .tcpOptions(TcpOptions.builder().
      sourcePortRange(PortRange.builder().min(80).max(81).build())
      .destinationPortRange(PortRange.builder().min(8080).max(8081).build()).build())
      .build()

    when:
    def rule = OracleSecurityRule.transform(egress)

    then:
    rule instanceof OracleSecurityRule
    rule.stateless == false
    rule.protocol == "TCP"
    rule.type == IngressSecurityRule.SourceType.CidrBlock.getValue()
    rule.typeDetail == "testCIDRBlock"
    rule.sourcePortRange.startPort == 80
    rule.sourcePortRange.endPort == 81
    rule.destinationPortRange.startPort == 8080
    rule.destinationPortRange.endPort == 8081
  }
}

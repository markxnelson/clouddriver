/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.model

import com.oracle.bmc.core.model.IngressSecurityRule
import com.oracle.bmc.core.model.PortRange
import com.oracle.bmc.core.model.TcpOptions
import spock.lang.Specification

class OracleIngressSecurityRuleSpec extends Specification {

  def "transform to IngressRule"() {
    setup:
    OracleIngressSecurityRule oracleSecurityRule = new OracleIngressSecurityRule(
      stateless: false,
      protocol: OracleSecurityRule.Protocol.TCP,
      sourceType: IngressSecurityRule.SourceType.CidrBlock,
      source: "testCIDRBlock",
      tcpOptions: new OraclePortRangeOptions(
        sourcePortRange: PortRange.builder().min(80).max(81).build(),
        destinationPortRange: PortRange.builder().min(8080).max(8081).build())
    )

    when:
    def ingress = oracleSecurityRule.transformIngressRule()

    then:
    ingress instanceof IngressSecurityRule
    ingress.isStateless == false
    ingress.protocol == "6"
    ingress.sourceType == IngressSecurityRule.SourceType.CidrBlock
    ingress.source == "testCIDRBlock"
    ingress.tcpOptions.sourcePortRange.min == 80
    ingress.tcpOptions.sourcePortRange.max == 81
    ingress.tcpOptions.destinationPortRange.min == 8080
    ingress.tcpOptions.destinationPortRange.max == 8081
  }

  def "transform from IngressRule"() {
    setup:
    IngressSecurityRule ingress = IngressSecurityRule.builder()
      .isStateless(false)
      .protocol("6")
      .sourceType(IngressSecurityRule.SourceType.CidrBlock)
      .source("testCIDRBlock")
      .tcpOptions(TcpOptions.builder().
      sourcePortRange(PortRange.builder().min(80).max(81).build())
      .destinationPortRange(PortRange.builder().min(8080).max(8081).build()).build())
      .build()

    when:
    def rule = OracleIngressSecurityRule.buildFromIngressRule(ingress)

    then:
    rule instanceof OracleIngressSecurityRule
    rule.stateless == false
    rule.protocol == "TCP"
    rule.sourceType == IngressSecurityRule.SourceType.CidrBlock
    rule.source == "testCIDRBlock"
    rule.tcpOptions.sourcePortRange.min == 80
    rule.tcpOptions.sourcePortRange.max == 81
    rule.tcpOptions.destinationPortRange.min == 8080
    rule.tcpOptions.destinationPortRange.max == 8081
  }
}

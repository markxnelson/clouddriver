/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.model

import com.oracle.bmc.core.model.EgressSecurityRule
import com.oracle.bmc.core.model.PortRange
import com.oracle.bmc.core.model.TcpOptions
import spock.lang.Specification

class OracleEgressSecurityRuleSpec extends Specification {

  def "transform to EgressRule"() {
    setup:
    OracleEgressSecurityRule oracleSecurityRule = new OracleEgressSecurityRule(
      stateless: false,
      protocol: OracleSecurityRule.Protocol.TCP,
      destinationType: EgressSecurityRule.DestinationType.CidrBlock,
      destination: "testCIDRBlock",
      tcpOptions: new OraclePortRangeOptions(
        sourcePortRange: PortRange.builder().min(80).max(81).build(),
        destinationPortRange: PortRange.builder().min(8080).max(8081).build())
    )

    when:
    def egress = oracleSecurityRule.transformEgressRule()

    then:
    egress instanceof EgressSecurityRule
    egress.isStateless == false
    egress.protocol == "6"
    egress.destinationType == EgressSecurityRule.DestinationType.CidrBlock
    egress.destination == "testCIDRBlock"
    egress.tcpOptions.sourcePortRange.min == 80
    egress.tcpOptions.sourcePortRange.max == 81
    egress.tcpOptions.destinationPortRange.min == 8080
    egress.tcpOptions.destinationPortRange.max == 8081
  }

  def "transform from EgressRule"() {
    setup:
    EgressSecurityRule egress = EgressSecurityRule.builder()
      .isStateless(false)
      .protocol("6")
      .destinationType(EgressSecurityRule.DestinationType.CidrBlock)
      .destination("testCIDRBlock")
      .tcpOptions(TcpOptions.builder().
      sourcePortRange(PortRange.builder().min(80).max(81).build())
      .destinationPortRange(PortRange.builder().min(8080).max(8081).build()).build())
      .build()

    when:
    def rule = OracleEgressSecurityRule.buildFromEgressRule(egress)

    then:
    rule instanceof OracleEgressSecurityRule
    rule.stateless == false
    rule.protocol == "TCP"
    rule.destinationType == EgressSecurityRule.DestinationType.CidrBlock
    rule.destination == "testCIDRBlock"
    rule.tcpOptions.sourcePortRange.min == 80
    rule.tcpOptions.sourcePortRange.max == 81
    rule.tcpOptions.destinationPortRange.min == 8080
    rule.tcpOptions.destinationPortRange.max == 8081
  }
}

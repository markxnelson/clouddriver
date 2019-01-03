/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.model;

import com.oracle.bmc.core.model.IngressSecurityRule;
import com.oracle.bmc.core.model.TcpOptions;
import com.oracle.bmc.core.model.UdpOptions;
import groovy.transform.EqualsAndHashCode;
import lombok.Data;

@Data
//@AllArgsConstructor
//@NoArgsConstructor
@EqualsAndHashCode(callSuper=true)
public class OracleIngressSecurityRule extends OracleSecurityRule {
  String source; // CIDR block or service name
  IngressSecurityRule.SourceType sourceType; // CIDR/service

  public IngressSecurityRule transformIngressRule() {
    TcpOptions tcp = null;
    if (protocol == Protocol.TCP) {
      if (tcpOptions != null) {
        tcp = TcpOptions.builder()
                .sourcePortRange(tcpOptions.sourcePortRange)
                .destinationPortRange(tcpOptions.destinationPortRange)
                .build();
      }
    }
    UdpOptions udp = null;
    if (protocol == Protocol.UDP) {
      if (udpOptions != null) {
        udp = UdpOptions.builder()
                .sourcePortRange(udpOptions.sourcePortRange)
                .destinationPortRange(udpOptions.destinationPortRange)
                .build();
      }
    }

    IngressSecurityRule obj = IngressSecurityRule.builder()
            .sourceType(sourceType)
            .source(source)
            .protocol(protocol.getNumber())
            .isStateless(stateless)
            .tcpOptions(tcp)
            .udpOptions(udp)
            .build();
    return obj;
  }

  public static OracleIngressSecurityRule buildFromIngressRule(IngressSecurityRule ingressSecurityRule) {
    OracleIngressSecurityRule rule = new OracleIngressSecurityRule();
    rule.stateless = ingressSecurityRule.getIsStateless();
    rule.protocol = OracleSecurityRule.Protocol.create(ingressSecurityRule.getProtocol());
    if (rule.protocol == Protocol.TCP) {
      rule.tcpOptions = new OraclePortRangeOptions();
      rule.tcpOptions.sourcePortRange = ingressSecurityRule.getTcpOptions().getSourcePortRange();
      rule.tcpOptions.destinationPortRange = ingressSecurityRule.getTcpOptions().getDestinationPortRange();
    }
    if (rule.protocol == Protocol.UDP) {
      rule.udpOptions = new OraclePortRangeOptions();
      rule.udpOptions.sourcePortRange = ingressSecurityRule.getUdpOptions().getSourcePortRange();
      rule.udpOptions.destinationPortRange = ingressSecurityRule.getUdpOptions().getDestinationPortRange();
    }
    rule.sourceType = ingressSecurityRule.getSourceType();
    rule.source = ingressSecurityRule.getSource();
    return rule;
  }
}

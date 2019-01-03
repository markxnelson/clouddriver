/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.model;

import com.oracle.bmc.core.model.EgressSecurityRule;
import com.oracle.bmc.core.model.TcpOptions;
import com.oracle.bmc.core.model.UdpOptions;
import groovy.transform.EqualsAndHashCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=true)
public class OracleEgressSecurityRule extends OracleSecurityRule {
  String destination; // CIDR block or service name
  EgressSecurityRule.DestinationType destinationType; // CIDR/service

  public EgressSecurityRule transformEgressRule() {
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

    EgressSecurityRule obj = EgressSecurityRule.builder()
            .destinationType(destinationType)
            .destination(destination)
            .protocol(protocol.getNumber())
            .isStateless(stateless)
            .tcpOptions(tcp)
            .udpOptions(udp)
            .build();
    return obj;
  }

  public static OracleEgressSecurityRule buildFromEgressRule(EgressSecurityRule egressSecurityRule) {
    OracleEgressSecurityRule rule = new OracleEgressSecurityRule();
    rule.stateless = egressSecurityRule.getIsStateless();
    rule.protocol = OracleSecurityRule.Protocol.create(egressSecurityRule.getProtocol());
    if (rule.protocol == Protocol.TCP) {
      rule.tcpOptions = new OraclePortRangeOptions();
      rule.tcpOptions.sourcePortRange = egressSecurityRule.getTcpOptions().getSourcePortRange();
      rule.tcpOptions.destinationPortRange = egressSecurityRule.getTcpOptions().getDestinationPortRange();
    }
    if (rule.protocol == Protocol.UDP) {
      rule.udpOptions = new OraclePortRangeOptions();
      rule.udpOptions.sourcePortRange = egressSecurityRule.getUdpOptions().getSourcePortRange();
      rule.udpOptions.destinationPortRange = egressSecurityRule.getUdpOptions().getDestinationPortRange();
    }
    rule.destinationType = egressSecurityRule.getDestinationType();
    rule.destination = egressSecurityRule.getDestination();
    return rule;
  }
}

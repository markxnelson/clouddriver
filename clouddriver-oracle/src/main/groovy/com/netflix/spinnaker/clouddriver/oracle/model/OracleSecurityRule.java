/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.model;

import com.google.common.collect.ImmutableSortedSet;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import com.oracle.bmc.core.model.EgressSecurityRule;
import com.oracle.bmc.core.model.IngressSecurityRule;
import com.oracle.bmc.core.model.TcpOptions;
import com.oracle.bmc.core.model.UdpOptions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.SortedSet;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OracleSecurityRule implements Rule {
  boolean stateless;
  // TODO: Support UDP/ICMP
  String protocol; // tcp/udp
  String type; // CIDR/service
  String typeDetail; // CIDR block or service name
  PortRange sourcePortRange;
  PortRange destinationPortRange;

  /**
   * The port ranges associated with this rule
   */
  @Override
  public SortedSet<PortRange> getPortRanges() {
    return ImmutableSortedSet.<PortRange>of(sourcePortRange, destinationPortRange);
  }

  @Override
  public String getProtocol() {
    return protocol;
  }

  private com.oracle.bmc.core.model.PortRange transformPortRange(PortRange range) {
    if (range != null) {
      return com.oracle.bmc.core.model.PortRange.builder()
              .min(range.getStartPort())
              .max(range.getEndPort())
              .build();
    }
    return null;
  }

  public IngressSecurityRule transformIngressRule() {
    TcpOptions tcp = null;
    if ("TCP".equalsIgnoreCase(protocol)) {
      tcp = TcpOptions.builder()
              .sourcePortRange(transformPortRange(sourcePortRange))
              .destinationPortRange(transformPortRange(destinationPortRange))
              .build();
    }
    UdpOptions udp = null;
    if ("UDP".equalsIgnoreCase(protocol)) {
      udp = UdpOptions.builder()
              .sourcePortRange(transformPortRange(sourcePortRange))
              .destinationPortRange(transformPortRange(destinationPortRange))
              .build();
    }

    IngressSecurityRule obj = IngressSecurityRule.builder()
            .sourceType(IngressSecurityRule.SourceType.create(type))
            .source(typeDetail)
            .protocol(protocol)
            .isStateless(stateless)
            .tcpOptions(tcp)
            .udpOptions(udp)
            .build();
    return obj;
  }

  public EgressSecurityRule transformEgressRule() {
    TcpOptions tcp = null;
    if ("TCP".equalsIgnoreCase(protocol)) {
      tcp = TcpOptions.builder()
              .sourcePortRange(transformPortRange(sourcePortRange))
              .destinationPortRange(transformPortRange(destinationPortRange))
              .build();
    }
    UdpOptions udp = null;
    if ("UDP".equalsIgnoreCase(protocol)) {
      udp = UdpOptions.builder()
              .sourcePortRange(transformPortRange(sourcePortRange))
              .destinationPortRange(transformPortRange(destinationPortRange))
              .build();
    }

    EgressSecurityRule obj = EgressSecurityRule.builder()
            .destinationType(EgressSecurityRule.DestinationType.create(type))
            .destination(typeDetail)
            .protocol(protocol)
            .isStateless(stateless)
            .tcpOptions(tcp)
            .udpOptions(udp)
            .build();
    return obj;
  }

  public static OracleSecurityRule transform(IngressSecurityRule ingressSecurityRule) {
    Rule.PortRange sourceRange = null;
    Rule.PortRange destinationRange = null;
    if (ingressSecurityRule.getTcpOptions() != null) {
      TcpOptions options = ingressSecurityRule.getTcpOptions();
      if (options.getSourcePortRange() != null) {
        sourceRange = new Rule.PortRange();
        sourceRange.setStartPort(options.getSourcePortRange().getMin());
        sourceRange.setEndPort(options.getSourcePortRange().getMax());
      }
      if (options.getDestinationPortRange() != null) {
        destinationRange = new Rule.PortRange();
        destinationRange.setStartPort(options.getDestinationPortRange().getMin());
        destinationRange.setEndPort(options.getDestinationPortRange().getMax());
      }
    }
    if (ingressSecurityRule.getUdpOptions() != null) {
      UdpOptions options = ingressSecurityRule.getUdpOptions();
      if (options.getSourcePortRange() != null) {
        sourceRange = new Rule.PortRange();
        sourceRange.setStartPort(options.getSourcePortRange().getMin());
        sourceRange.setEndPort(options.getSourcePortRange().getMax());
      }
      if (options.getDestinationPortRange() != null) {
        destinationRange = new Rule.PortRange();
        destinationRange.setStartPort(options.getDestinationPortRange().getMin());
        destinationRange.setEndPort(options.getDestinationPortRange().getMax());
      }
    }

    OracleSecurityRule rule = new OracleSecurityRule();
    rule.stateless = ingressSecurityRule.getIsStateless();
    rule.protocol = ingressSecurityRule.getProtocol();
    rule.sourcePortRange = sourceRange;
    rule.destinationPortRange = destinationRange;
    if (ingressSecurityRule.getSourceType() != null) {
      rule.type = ingressSecurityRule.getSourceType().getValue();
    }
    rule.typeDetail = ingressSecurityRule.getSource();
    return rule;
  }

  public static OracleSecurityRule transform(EgressSecurityRule egressSecurityRule) {
    Rule.PortRange sourceRange = null;
    Rule.PortRange destinationRange = null;
    if (egressSecurityRule.getTcpOptions() != null) {
      TcpOptions options = egressSecurityRule.getTcpOptions();
      if (options.getSourcePortRange() != null) {
        sourceRange = new Rule.PortRange();
        sourceRange.setStartPort(options.getSourcePortRange().getMin());
        sourceRange.setEndPort(options.getSourcePortRange().getMax());
      }
      if (options.getDestinationPortRange() != null) {
        destinationRange = new Rule.PortRange();
        destinationRange.setStartPort(options.getDestinationPortRange().getMin());
        destinationRange.setEndPort(options.getDestinationPortRange().getMax());
      }
    }
    if (egressSecurityRule.getUdpOptions() != null) {
      UdpOptions options = egressSecurityRule.getUdpOptions();
      if (options.getSourcePortRange() != null) {
        sourceRange = new Rule.PortRange();
        sourceRange.setStartPort(options.getSourcePortRange().getMin());
        sourceRange.setEndPort(options.getSourcePortRange().getMax());
      }
      if (options.getDestinationPortRange() != null) {
        destinationRange = new Rule.PortRange();
        destinationRange.setStartPort(options.getDestinationPortRange().getMin());
        destinationRange.setEndPort(options.getDestinationPortRange().getMax());
      }
    }

    OracleSecurityRule rule = new OracleSecurityRule();
    rule.stateless = egressSecurityRule.getIsStateless();
    rule.protocol = egressSecurityRule.getProtocol();
    rule.sourcePortRange = sourceRange;
    rule.destinationPortRange = destinationRange;
    if (egressSecurityRule.getDestinationType() != null) {
      rule.type = egressSecurityRule.getDestinationType().getValue();
    }
    rule.typeDetail = egressSecurityRule.getDestination();
    return rule;
  }
}

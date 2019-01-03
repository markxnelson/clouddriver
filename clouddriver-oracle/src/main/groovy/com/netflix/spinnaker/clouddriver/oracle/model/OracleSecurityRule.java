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
import lombok.Data;

import java.util.Collections;
import java.util.SortedSet;

// TODO Support ICMP
@Data
public abstract class OracleSecurityRule implements Rule {
  boolean stateless;
  Protocol protocol;
  OraclePortRangeOptions tcpOptions;
  OraclePortRangeOptions udpOptions;

  /**
   * The port ranges associated with this rule
   */
  @Override
  public SortedSet<PortRange> getPortRanges() {
    if (protocol == Protocol.TCP) {
      if (tcpOptions != null) {
        return merge(
                transformPortRange(tcpOptions.sourcePortRange),
                transformPortRange(tcpOptions.destinationPortRange));
      }
    } else if (protocol == Protocol.UDP) {
      if (udpOptions != null) {
        return merge(
                transformPortRange(udpOptions.sourcePortRange),
                transformPortRange(udpOptions.destinationPortRange));
      }
    }
    return Collections.emptySortedSet();
  }

  private SortedSet<PortRange> merge(PortRange source, PortRange destination) {
    if (source == null) {
      if (destination != null) {
        return ImmutableSortedSet.<PortRange>of(destination);
      } else {
        return Collections.emptySortedSet();
      }
    } else {
      if (destination != null) {
        return ImmutableSortedSet.<PortRange>of(source, destination);
      } else {
        return ImmutableSortedSet.<PortRange>of(source);
      }
    }
  }

  @Override
  public String getProtocol() {
    return protocol.name();
  }

  private PortRange transformPortRange(com.oracle.bmc.core.model.PortRange range) {
    if (range != null) {
      PortRange result = new PortRange();
      result.setStartPort(range.getMin());
      result.setEndPort(range.getMax());
      return result;
    }
    return null;
  }

  /**
   * The transport protocol. Specify either `all` or an IPv4 protocol number as
   * defined in
   * [Protocol Numbers](http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml).
   * Options are supported only for ICMP (\"1\"), TCP (\"6\"), and UDP (\"17\").
   **/
  enum Protocol {
    // * Options are supported only for ICMP (\"1\"), TCP (\"6\"), and UDP (\"17\").
    ICMP("1"),
    TCP("6"),
    UDP("17"),
    ALL("all");

    private String number;

    public String getNumber(){
      return this.number;
    }
    
    private Protocol(String number){
      this.number = number;
    }

    private static java.util.Map<String, Protocol> map;

    static {
      map = new java.util.HashMap<>();
      for (Protocol p : Protocol.values()) {
          map.put(p.getNumber(), p);
      }
    }

    public static Protocol create(String number) {
      if (map.containsKey(number)) {
        return map.get(number);
      }
      return Protocol.ALL;
    }
  }
}

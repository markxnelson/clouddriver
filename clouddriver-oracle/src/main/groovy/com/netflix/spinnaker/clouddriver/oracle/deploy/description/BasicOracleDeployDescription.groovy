/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.description

import static com.google.common.base.Strings.isNullOrEmpty
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import com.oracle.bmc.core.model.CreateInstancePoolPlacementConfigurationDetails
import groovy.transform.AutoClone
import groovy.transform.Canonical
import groovy.transform.ToString

@AutoClone
@Canonical
@ToString(includeSuper = true, includeNames = true)
class BasicOracleDeployDescription extends BaseOracleInstanceDescription implements DeployDescription, ApplicationNameable {

  String application
  String stack
  String freeFormDetails
  String loadBalancerId
  String backendSetName
  ServerGroup.Capacity capacity
  List<CreateInstancePoolPlacementConfigurationDetails> placements
  //targetSize takes precedence if targetSize and capacity.desired are both specified.
  Integer targetSize

  @Override
  Collection<String> getApplications() {
    return [application]
  }

  //see NameBuilder.combineAppStackDetail
  public String qualifiedName() {
    String stck = isNullOrEmpty(this.stack)? this.stack : "";
    if (isNullOrEmpty(freeFormDetails)) {
      return this.application + "-" + stck + "-" + freeFormDetails;
    }
    if (!stack.isEmpty()) {
      return this.application + "-" + stack;
    }
    return this.application;
  }

  public int targetSize() {
    if (targetSize != null) {
      return targetSize;
    } else {
      if (capacity != null && capacity.getDesired() != null) {
        return capacity.getDesired();
      } else {
        return 0;
      }
    }
  }
}

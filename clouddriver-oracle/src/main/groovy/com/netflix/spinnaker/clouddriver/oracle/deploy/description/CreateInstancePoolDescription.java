/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. If a copy of the Apache License Version
 * 2.0 was not distributed with this file, You can obtain one at
 * https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.description;

import static com.google.common.base.Strings.isNullOrEmpty;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.oracle.bmc.core.model.CreateInstancePoolPlacementConfigurationDetails;
import java.util.List;
import groovy.transform.ToString;

//TODO replace BasicOracleDeployDescription and BaseOracleInstanceDescription
@ToString
public class CreateInstancePoolDescription extends AbstractOracleCredentialsDescription {

  private String imageId;
  private String shape;
  private String region;
//  private String availabilityDomain;
//  private String vpcId; //TDODO removed?
//  private String subnetId;
  private List<CreateInstancePoolPlacementConfigurationDetails> placements;
  private String accountName;
  private String sshAuthorizedKeys;
  private String application;
  private String stack;
  private String freeFormDetails;
  private ServerGroup.Capacity capacity;
  // targetSize takes precedence if targetSize and capacity.desired are both specified.
  private Integer targetSize;
  private String loadBalancerId;
  private String backendSetName;

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

  public String getImageId() {
    return imageId;
  }

  public void setImageId(String imageId) {
    this.imageId = imageId;
  }

  public String getShape() {
    return shape;
  }

  public void setShape(String shape) {
    this.shape = shape;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

//  public String getAvailabilityDomain() {
//    return availabilityDomain;
//  }
//
//  public void setAvailabilityDomain(String availabilityDomain) {
//    this.availabilityDomain = availabilityDomain;
//  }

  public List<CreateInstancePoolPlacementConfigurationDetails> getPlacements() {
    return placements;
  }

  public void setPlacements(List<CreateInstancePoolPlacementConfigurationDetails> placements) {
    this.placements = placements;
  }

//  public String getVpcId() {
//    return vpcId;
//  }
//
//  public void setVpcId(String vpcId) {
//    this.vpcId = vpcId;
//  }
//
//  public String getSubnetId() {
//    return subnetId;
//  }
//
//  public void setSubnetId(String subnetId) {
//    this.subnetId = subnetId;
//  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getSshAuthorizedKeys() {
    return sshAuthorizedKeys;
  }

  public void setSshAuthorizedKeys(String sshAuthorizedKeys) {
    this.sshAuthorizedKeys = sshAuthorizedKeys;
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

  public String getFreeFormDetails() {
    return freeFormDetails;
  }

  public void setFreeFormDetails(String freeFormDetails) {
    this.freeFormDetails = freeFormDetails;
  }

  public ServerGroup.Capacity getCapacity() {
    return capacity;
  }

  public void setCapacity(ServerGroup.Capacity capacity) {
    this.capacity = capacity;
  }

  public Integer getTargetSize() {
    return targetSize;
  }

  public void setTargetSize(Integer targetSize) {
    this.targetSize = targetSize;
  }

  public String getLoadBalancerId() {
    return loadBalancerId;
  }

  public void setLoadBalancerId(String loadBalancerId) {
    this.loadBalancerId = loadBalancerId;
  }

  public String getBackendSetName() {
    return backendSetName;
  }

  public void setBackendSetName(String backendSetName) {
    this.backendSetName = backendSetName;
  }
}

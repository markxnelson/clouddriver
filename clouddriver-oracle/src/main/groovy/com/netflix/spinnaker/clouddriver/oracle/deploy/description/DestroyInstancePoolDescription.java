package com.netflix.spinnaker.clouddriver.oracle.deploy.description;

public class DestroyInstancePoolDescription extends AbstractOracleCredentialsDescription {
  private String accountName;
  private String region;
  private String serverGroupName;
  private String instancePoolId;
  private String instanceConfigId;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getServerGroupName() {
    return serverGroupName;
  }

  public void setServerGroupName(String serverGroupName) {
    this.serverGroupName = serverGroupName;
  }

  public String getInstancePoolId() {
    return instancePoolId;
  }

  public void setInstancePoolId(String instancePoolId) {
    this.instancePoolId = instancePoolId;
  }

  public String getInstanceConfigId() {
    return instanceConfigId;
  }

  public void setInstanceConfigId(String instanceConfigId) {
    this.instanceConfigId = instanceConfigId;
  }
}

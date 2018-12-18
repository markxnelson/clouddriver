/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.service.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.Region
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.ComputeManagementClient
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.core.model.Instance
import com.oracle.bmc.core.model.InstancePool
import com.oracle.bmc.core.model.InstanceSummary
import com.oracle.bmc.core.model.Vnic
import com.oracle.bmc.core.model.VnicAttachment
import com.oracle.bmc.core.requests.LaunchInstanceRequest
import com.oracle.bmc.core.responses.GetInstancePoolResponse
import com.oracle.bmc.core.responses.GetVnicResponse
import com.oracle.bmc.core.responses.LaunchInstanceResponse
import com.oracle.bmc.core.responses.ListInstancePoolInstancesResponse
import com.oracle.bmc.core.responses.ListVnicAttachmentsResponse
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.Backend
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.HealthChecker
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.loadbalancer.responses.GetLoadBalancerResponse
import com.oracle.bmc.loadbalancer.responses.UpdateBackendSetResponse
import spock.lang.Specification

class DefaultOracleServerGroupServiceSpec extends Specification {

  def "create server group"() {
    setup:
    def SSHKeys = "ssh-rsa ABC a@b"
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def task = Mock(Task)
    def sgService = new DefaultOracleServerGroupService(persistence)

    when:
    def sg = new OracleServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "sshAuthorizedKeys" : SSHKeys,
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 4,
      credentials: creds
    )
    sgService.createServerGroup(task, sg)

    then:
    4 * creds.computeClient.launchInstance(_) >> { args ->
      LaunchInstanceRequest argumentRequest = (LaunchInstanceRequest) args[0]
      assert argumentRequest.getLaunchInstanceDetails().getMetadata().get("ssh_authorized_keys") == SSHKeys
      return LaunchInstanceResponse.builder().instance(Instance.builder().timeCreated(new Date()).build()).build()
    }
    1 * persistence.upsertServerGroup(_)
  }

  def "create server group over limit"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def task = Mock(Task)
    def sgService = new DefaultOracleServerGroupService(persistence)

    when:
    def sg = new OracleServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 3,
      credentials: creds
    )
    sgService.createServerGroup(task, sg)

    then:
    3 * creds.computeClient.launchInstance(_) >> launchResponse() >> launchResponse() >>
      { throw new com.oracle.bmc.model.BmcException(400, 'LimitExceeded', 'LimitExceeded', 'LimitExceeded')  }
    1 * persistence.upsertServerGroup(_) >> { args ->
      OracleServerGroup serverGroup = (OracleServerGroup) args[0]
      assert serverGroup.instances.size() == 2
    }
  }

  def "resize (increase) server group"() {
    setup:
    def SSHKeys = null
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "sshAuthorizedKeys" : SSHKeys,
        "createdTime"       : System.currentTimeMillis()
      ],
      instances: [
        new OracleInstance(name: "a")
      ],
      targetSize: 1,
      credentials: creds
    )

    when:
    def resized = sgService.resizeServerGroup(task, creds, "sg1", 5)

    then:
    4 * creds.computeClient.launchInstance(_)  >> { args ->
      LaunchInstanceRequest argumentRequest = (LaunchInstanceRequest) args[0]
      assert argumentRequest.getLaunchInstanceDetails().getMetadata().get("ssh_authorized_keys") == SSHKeys
      return LaunchInstanceResponse.builder().instance(Instance.builder().timeCreated(new Date()).build()).build()
    }
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_) >> { args ->
      OracleServerGroup serverGroup = (OracleServerGroup) args[0]
      assert serverGroup.instances.size() == 5
      assert serverGroup.targetSize == 5
    }
    resized == true
  }

  def "resize (increase) server group over limit"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      instances: [
        new OracleInstance(name: "a")
      ],
      targetSize: 1,
      credentials: creds
    )

    when:
    def resized = sgService.resizeServerGroup(task, creds, "sg1", 5)

    then:
    4 * creds.computeClient.launchInstance(_) >>
      launchResponse() >>
      launchResponse() >>
      launchResponse() >>
      { throw new com.oracle.bmc.model.BmcException(400, 'LimitExceeded', 'LimitExceeded', 'LimitExceeded')  }
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_) >> { args ->
      OracleServerGroup serverGroup = (OracleServerGroup) args[0]
      assert serverGroup.instances.size() == 4
      // serverGroup.targetSize should be 5 as requested
      assert serverGroup.targetSize == 5
    }
    resized == true
  }

  def "resize (decrease) server group"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      instances: [
        new OracleInstance(name: "a"),
        new OracleInstance(name: "b"),
        new OracleInstance(name: "c"),
        new OracleInstance(name: "d"),
        new OracleInstance(name: "e")
      ],
      targetSize: 5,
      credentials: creds
    )

    when:
    def resized = sgService.resizeServerGroup(task, creds, "sg1", 1)

    then:
    4 * creds.computeClient.terminateInstance(_)
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_)
    resized == true
  }

  def "enable server group"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 1,
      credentials: creds,
      disabled: true
    )

    when:
    sgService.enableServerGroup(task, creds, "sg1")

    then:
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_)
    sg.disabled == false
  }

  def "disable server group"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 1,
      credentials: creds,
      disabled: false
    )

    when:
    sgService.disableServerGroup(task, creds, "sg1")

    then:
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    1 * persistence.upsertServerGroup(_)
    sg.disabled == true
  }

  def "destroy server group"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def task = Mock(Task)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
      name: "sg1",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      instances: [
        new OracleInstance(name: "a"),
        new OracleInstance(name: "b"),
        new OracleInstance(name: "c"),
        new OracleInstance(name: "d"),
        new OracleInstance(name: "e")
      ],
      targetSize: 5,
      credentials: creds,
      disabled: false
    )

    when:
    sgService.destroyServerGroup(task, creds, "sg1")

    then:
    1 * persistence.getServerGroupByName(_, "sg1") >> sg
    5 * creds.computeClient.terminateInstance(_)
    1 * persistence.deleteServerGroup(sg)
  }

  def "get server group"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
      name: "foo-v001",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 5,
      credentials: creds,
      disabled: false
    )

    when:
    sgService.getServerGroup(creds, "foo", "foo-v001")

    then:
    1 * persistence.listServerGroupNames(_) >> ["foo-v001"]
    1 * persistence.getServerGroupByName(_, "foo-v001") >> sg
  }

  def "list all server group"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
      name: "foo-v001",
      region: creds.region,
      zone: "ad1",
      launchConfig: [
        "availabilityDomain": "ad1",
        "compartmentId"     : "ocid.compartment.123",
        "imageId"           : "ocid.image.123",
        "shape"             : "small",
        "vpcId"             : "ocid.vcn.123",
        "subnetId"          : "ocid.subnet.123",
        "createdTime"       : System.currentTimeMillis()
      ],
      targetSize: 5,
      credentials: creds,
      disabled: false
    )

    when:
    def serverGroups = sgService.listAllServerGroups(creds)

    then:
    1 * persistence.listServerGroupNames(_) >> ["foo-v001", "bar-v001", "bbq-v001", "foo-test-v001"]
    4 * persistence.getServerGroupByName(_, _) >> sg
    serverGroups.size() == 4
  }

  def "list server group names by cluster"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_PHOENIX_1.regionId
    creds.getComputeClient() >> Mock(ComputeClient)
    def persistence = Mock(OracleServerGroupPersistence)
    def sgService = new DefaultOracleServerGroupService(persistence)

    when:
    def serverGroups = sgService.listServerGroupNamesByClusterName(creds, "foo-test")

    then:
    1 * persistence.listServerGroupNames(_) >> ["foo-test-v001", "foo-v002", "foo-edge-v001", "foo-test-v002", "bar-v001"]
    serverGroups == ["foo-test-v001", "foo-test-v002"]
  }

  LaunchInstanceResponse launchResponse() {
    LaunchInstanceResponse.builder().instance(
      Instance.builder().timeCreated(new Date()).build()).build()
  }

  def "poll instances 2 targeting 0"() {
    setup:
    def task = Mock(Task)
    def creds = mockCreds()
    def computeClient = creds.computeClient
    def computeManagementClient = creds.computeManagementClient
    VirtualNetworkClient networkClient = creds.networkClient
    def persistence = Mock(OracleServerGroupPersistence)
    def service = new DefaultOracleServerGroupService(persistence)
    def instances = ['129.1.1.1', '129.1.1.2']
    def sg = new OracleServerGroup(
      name: "scaling-v20",
      region: creds.region,
      zone: "ad1",
      targetSize: 0,
      credentials: creds,
      instancePoolId: "ocid.instancePool.123",
      disabled: false,
      instances: instanceSet(instances as String[])
    )
    GetInstancePoolResponse res = GetInstancePoolResponse.builder()
      .instancePool(InstancePool.builder().size(0).build()).build()

    when:
    service.poll(task, sg, 1, 1)

    then:
    1 * computeManagementClient.getInstancePool(_) >> res
    3 * computeManagementClient.listInstancePoolInstances(_) >>
      listInstances(instances as String[]) >> listInstances(instances[1]) >> listInstances()
    0 * computeClient.listVnicAttachments(_)
    0 * networkClient.getVnic(_)
  }

  def "poll instances 3 targeting 1"() {
    setup:
    def task = Mock(Task)
    def creds = mockCreds()
    def computeClient = creds.computeClient
    def computeManagementClient = creds.computeManagementClient
    VirtualNetworkClient networkClient = creds.networkClient
    def persistence = Mock(OracleServerGroupPersistence)
    def service = new DefaultOracleServerGroupService(persistence)
    def instances = ['129.1.1.1', '129.1.1.2', '129.1.1.3']
    def sg = new OracleServerGroup(
      name: "scaling-v31",
      region: creds.region,
      zone: "ad1",
      targetSize: 1,
      credentials: creds,
      instancePoolId: "ocid.instancePool.123",
      disabled: false,
      instances: instanceSet(instances as String[])
    )

    when:
    service.poll(task, sg, 1, 1)

    then:
    1 * computeManagementClient.getInstancePool(_) >> GetInstancePoolResponse.builder()
      .instancePool(InstancePool.builder().size(1).build()).build()
    3 * computeManagementClient.listInstancePoolInstances(_) >>
      listInstances(instances as String[]) >>
      listInstances(instances[1], instances[2]) >>
      listInstances(instances[1])
    0 * computeClient.listVnicAttachments(_)
    0 * networkClient.getVnic(_)
  }

  def "poll instances 0 targeting 3"() {
    setup:
    def task = Mock(Task)
    def creds = mockCreds()
    def computeClient = creds.computeClient
    def computeManagementClient = creds.computeManagementClient
    VirtualNetworkClient networkClient = creds.networkClient
    def persistence = Mock(OracleServerGroupPersistence)
    def service = new DefaultOracleServerGroupService(persistence)
    def sg = new OracleServerGroup(
      name: "scaling-v03",
      region: creds.region,
      zone: "ad1",
      targetSize: 3,
      credentials: creds,
      instancePoolId: "ocid.instancePool.123",
      disabled: false
    )
    GetInstancePoolResponse res = GetInstancePoolResponse.builder()
      .instancePool(InstancePool.builder().build()).build()

    when:
    service.poll(task, sg, 1, 1)

    then:
    1 * computeManagementClient.getInstancePool(_) >> GetInstancePoolResponse.builder()
      .instancePool(InstancePool.builder().size(3).build()).build()
    2 * computeManagementClient.listInstancePoolInstances(_) >>
      listInstances('ins.1') >> listInstances('ins.1','ins.2','ins.3')
    3 * computeClient.listVnicAttachments(_) >>
      listVnicAtt('vnic.1') >> listVnicAtt('vnic.2') >> listVnicAtt('vnic.3')
    3 * networkClient.getVnic(_) >>
      vnic('129.0.0.1') >> vnic('129.0.0.2') >> vnic('129.0.0.3')
  }

  def "poll instances 1 targeting 3"() {
    setup:
    def task = Mock(Task)
    def creds = mockCreds()
    def computeClient = creds.computeClient
    def computeManagementClient = creds.computeManagementClient
    VirtualNetworkClient networkClient = creds.networkClient
    def persistence = Mock(OracleServerGroupPersistence)
    def service = new DefaultOracleServerGroupService(persistence)
    def instances = ['129.1.1.1', '129.1.1.2', '129.1.1.3']
    def sg = new OracleServerGroup(
      name: "scaling-v13",
      region: creds.region,
      zone: "ad1",
      targetSize: 3,
      credentials: creds,
      instancePoolId: "ocid.instancePool.123",
      disabled: false,
      instances: instanceSet(instances[0])
    )

    when:
    service.poll(task, sg, 1, 1)

    then:
    1 * computeManagementClient.getInstancePool(_) >> GetInstancePoolResponse.builder()
      .instancePool(InstancePool.builder().size(3).build()).build()
    3 * computeManagementClient.listInstancePoolInstances(_) >>
      listInstances(instances[0]) >>
      listInstances(instances[0], instances[1]) >>
      listInstances(instances as String[])
    2 * computeClient.listVnicAttachments(_) >>
      listVnicAtt('vnic.2') >> listVnicAtt('vnic.3')
    2 * networkClient.getVnic(_) >>
      vnic('129.1.1.2') >>
      vnic('129.1.1.3')
  }


  ListInstancePoolInstancesResponse listInstances(String... ids) {
    ListInstancePoolInstancesResponse.builder().items(ids.collect{ id ->
      InstanceSummary.builder().id(id).state('Provisioning').timeCreated(new Date()).build()
    } as List).build()
  }

  ListVnicAttachmentsResponse listVnicAtt(String id) {
    ListVnicAttachmentsResponse.builder().items([VnicAttachment.builder().vnicId(id).build()]).build()
  }

  GetVnicResponse vnic(String ip) {
    GetVnicResponse.builder().vnic(Vnic.builder().privateIp(ip).build()).build()
  }


  Set<OracleInstance> instanceSet(String... addresses) {
    addresses.collect {new OracleInstance(id: it, privateIp: it)} as Set
  }

  OracleNamedAccountCredentials mockCreds() {
    def creds = Mock(OracleNamedAccountCredentials)
    def computeClient = Mock(ComputeClient)
    def computeManagementClient = Mock(ComputeManagementClient)
    VirtualNetworkClient networkClient = Mock(VirtualNetworkClient)
    creds.getName() >> "foo"
    creds.getRegion() >> Region.US_ASHBURN_1.regionId
    creds.getComputeClient() >> computeClient
    creds.getComputeManagementClient() >> computeManagementClient
    creds.getNetworkClient() >> networkClient
    def loadBalancerClient = Mock(LoadBalancerClient)
    creds.loadBalancerClient >> loadBalancerClient
    return creds
  }

  def "updateLoadBalancer BackendSet from 2 to 4"() {
    setup:
    def task = Mock(Task)
    def creds = mockCreds()
    def computeClient = creds.computeClient
    def loadBalancerClient = creds.loadBalancerClient
    def persistence = Mock(OracleServerGroupPersistence)
    def service = new DefaultOracleServerGroupService(persistence)
    OracleWorkRequestPoller.poller = Mock(OracleWorkRequestPoller)
    def sg = new OracleServerGroup(
      name: "sg-v001",
      region: creds.region,
      targetSize: 2,
      credentials: creds,
      loadBalancerId: "ocid.lb.oc1..12345",
      backendSetName: "sg1BackendSet",
      disabled: false
    )
    def backends = ['10.1.20.1', '10.1.20.2', '10.1.20.3','10.1.20.4']
    def srvGroup = ['10.1.20.2', '10.1.20.4']
    def newGroup = ['10.1.20.2', '10.1.20.4', '10.1.20.5', '10.1.20.6']
    Set<OracleInstance> oldSet = instanceSet(srvGroup as String[])
    Set<OracleInstance> newSet = instanceSet(newGroup as String[])

    when:
    service.updateLoadBalancer(task, sg, oldSet, newSet)

    then:
    1 * loadBalancerClient.getLoadBalancer(_) >> GetLoadBalancerResponse.builder()
      .loadBalancer(LoadBalancer.builder()
      .backendSets(["sg1BackendSet": BackendSet.builder()
      .healthChecker(HealthChecker.builder().build())
      .backends( backends.collect { Backend.builder().ipAddress(it).build() } )
      .build()]).build()).build()
    1 * loadBalancerClient.updateBackendSet(_) >> { args ->
      UpdateBackendSetRequest req = (UpdateBackendSetRequest) args[0]
      def updatedBackendSet = req.updateBackendSetDetails.backends.collect {it.ipAddress}
      assert updatedBackendSet.size() == 6
      assert updatedBackendSet.contains('10.1.20.1')
      assert updatedBackendSet.contains('10.1.20.2')
      assert updatedBackendSet.contains('10.1.20.5')
      assert updatedBackendSet.contains('10.1.20.6')
      UpdateBackendSetResponse.builder().opcWorkRequestId("wr1").build()
    }
    1 * OracleWorkRequestPoller.poller.wait("wr1", _, _, loadBalancerClient) >> null
//  1 * OracleWorkRequestPoller.       poll("wr1", _, _, loadBalancerClient) >> null
  }

  def "updateLoadBalancer BackendSet from 3 to 1"() {
    setup:
    def task = Mock(Task)
    def creds = mockCreds()
    def computeClient = creds.computeClient
    def loadBalancerClient = creds.loadBalancerClient
    def persistence = Mock(OracleServerGroupPersistence)
    def service = new DefaultOracleServerGroupService(persistence)
//    GroovySpy(OracleWorkRequestPoller, global: true)
    OracleWorkRequestPoller.poller = Mock(OracleWorkRequestPoller)
    def sg = new OracleServerGroup(
      name: "sg-v001",
      region: creds.region,
      targetSize: 3,
      credentials: creds,
      loadBalancerId: "ocid.lb.oc1..12345",
      backendSetName: "sg1BackendSet",
      disabled: false
    )
    def backends = ['10.1.20.1', '10.1.20.2', '10.1.20.3','10.1.20.4', '10.1.20.5', '10.1.20.6']
    def srvGroup = ['10.1.20.2', '10.1.20.4', '10.1.20.6']
    def newGroup = ['10.1.20.4']
    Set<OracleInstance> oldSet = instanceSet(srvGroup as String[])
    Set<OracleInstance> newSet = instanceSet(newGroup as String[])

    when:
    service.updateLoadBalancer(task, sg, oldSet, newSet)

    then:
    1 * loadBalancerClient.getLoadBalancer(_) >> GetLoadBalancerResponse.builder()
      .loadBalancer(LoadBalancer.builder()
      .backendSets(["sg1BackendSet": BackendSet.builder()
      .healthChecker(HealthChecker.builder().build())
      .backends( backends.collect { Backend.builder().ipAddress(it).build() } )
      .build()]).build()).build()
    1 * loadBalancerClient.updateBackendSet(_) >> { args ->
      UpdateBackendSetRequest req = (UpdateBackendSetRequest) args[0]
      def updatedBackendSet = req.updateBackendSetDetails.backends.collect {it.ipAddress}
      assert updatedBackendSet.size() == 4
      assert updatedBackendSet.contains('10.1.20.1')
      assert updatedBackendSet.contains('10.1.20.3')
      assert updatedBackendSet.contains('10.1.20.5')
      assert updatedBackendSet.contains('10.1.20.4')
      UpdateBackendSetResponse.builder().opcWorkRequestId("wr1").build()
    }
//    1 * OracleWorkRequestPoller.poll("wr1", _, _, loadBalancerClient) >> null
    1 * OracleWorkRequestPoller.poller.wait("wr1", _, _, loadBalancerClient) >> null
  }

  def "updateLoadBalancer BackendSet same size"() {
    setup:
    def task = Mock(Task)
    def creds = mockCreds()
    def computeClient = creds.computeClient
    def loadBalancerClient = creds.loadBalancerClient
    def persistence = Mock(OracleServerGroupPersistence)
    def service = new DefaultOracleServerGroupService(persistence)
//    GroovySpy(OracleWorkRequestPoller, global: true)
    OracleWorkRequestPoller.poller = Mock(OracleWorkRequestPoller)
    def sg = new OracleServerGroup(
      name: "sg-v001",
      region: creds.region,
      targetSize: 3,
      credentials: creds,
      loadBalancerId: "ocid.lb.oc1..12345",
      backendSetName: "sg1BackendSet",
      disabled: false
    )
    def backends = ['10.1.20.1', '10.1.20.2', '10.1.20.3','10.1.20.4', '10.1.20.5', '10.1.20.6']
    def srvGroup = ['10.1.20.2', '10.1.20.4']
    def newGroup =  ['10.1.20.2', '10.1.20.4']
    Set<OracleInstance> oldSet = instanceSet(srvGroup as String[])
    Set<OracleInstance> newSet = instanceSet(newGroup as String[])

    when:
    service.updateLoadBalancer(task, sg, oldSet, newSet)

    then:
    0 * loadBalancerClient.getLoadBalancer(_)
    0 * loadBalancerClient.updateBackendSet(_)
  }
}

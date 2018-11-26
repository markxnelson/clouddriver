package com.netflix.spinnaker.clouddriver.oracle.deploy.op;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.BasicOracleDeployDescription;
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider;
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.oracle.bmc.core.ComputeManagementClient;
import com.oracle.bmc.core.model.ComputeInstanceDetails;
import com.oracle.bmc.core.model.CreateInstanceConfigurationDetails;
import com.oracle.bmc.core.model.CreateInstancePoolDetails;
import com.oracle.bmc.core.model.InstanceConfiguration;
import com.oracle.bmc.core.model.InstanceConfigurationCreateVnicDetails;
import com.oracle.bmc.core.model.InstanceConfigurationInstanceSourceViaImageDetails;
import com.oracle.bmc.core.model.InstanceConfigurationLaunchInstanceDetails;
import com.oracle.bmc.core.model.InstancePool;
import com.oracle.bmc.core.requests.CreateInstanceConfigurationRequest;
import com.oracle.bmc.core.requests.CreateInstancePoolRequest;
import com.oracle.bmc.core.responses.CreateInstanceConfigurationResponse;
import com.oracle.bmc.core.responses.CreateInstancePoolResponse;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class CreateInstancePoolAtomicOperation implements AtomicOperation<Void> {

//TODO  private final CreateInstancePoolDescription desc;
  private final BasicOracleDeployDescription desc;

  private static final String CREATE_PHASE = "CREATE_SERVER_GROUP";

  private static Task task() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired
  OracleServerGroupService oracleServerGroupService;

  @Autowired
  OracleClusterProvider clusterProvider;

  public CreateInstancePoolAtomicOperation(BasicOracleDeployDescription description) {
    this.desc = description;
  }

  /*
   * Create, Delete, No Edit yet. - imageId = desc.getImageId() - shape("VM.Standard2.1") -
   * vnicDetails? - blockVolumes?
   */
  InstanceConfiguration instanceConfig() {
    ComputeManagementClient client = desc.getCredentials().getComputeManagementClient();
    String imageId = desc.getImageId();
    String compartmentId = desc.getCredentials().getCompartmentId();

    InstanceConfigurationCreateVnicDetails vnicDetails =
        InstanceConfigurationCreateVnicDetails.builder().build();

    InstanceConfigurationInstanceSourceViaImageDetails sourceDetails =
        InstanceConfigurationInstanceSourceViaImageDetails.builder().imageId(imageId).build();

    InstanceConfigurationLaunchInstanceDetails launchDetails =
        InstanceConfigurationLaunchInstanceDetails.builder().compartmentId(compartmentId)
            .displayName(desc.qualifiedName() + "-instance")// instance display name
            .createVnicDetails(vnicDetails).shape("VM.Standard2.1").sourceDetails(sourceDetails)
            .build();

    ComputeInstanceDetails instanceDetails =
        ComputeInstanceDetails.builder().launchDetails(launchDetails)
            .secondaryVnics(Collections.EMPTY_LIST).blockVolumes(Collections.EMPTY_LIST).build();

    CreateInstanceConfigurationDetails configuDetails =
        CreateInstanceConfigurationDetails.builder().displayName(desc.qualifiedName() + "-config")
            .compartmentId(compartmentId).instanceDetails(instanceDetails).build();

    CreateInstanceConfigurationRequest req = CreateInstanceConfigurationRequest.builder()
        .createInstanceConfiguration(configuDetails).build();

    CreateInstanceConfigurationResponse response = client.createInstanceConfiguration(req);
    return response.getInstanceConfiguration();
  }

  /* - create/select(withID) InstanceConfiguration
   * - CreateInstancePoolPlacementConfigurationDetails
   * - desc.targetSize()
   */
  InstancePool createInstancePool() {
    InstanceConfiguration instanceConfiguration = instanceConfig();
//    String subnetId = desc.getSubnetId();
//    String[] availabilityDomains = desc.getAvailabilityDomain();

    ComputeManagementClient client = desc.getCredentials().getComputeManagementClient();
    String compartmentId = desc.getCredentials().getCompartmentId();

//    CreateInstancePoolPlacementConfigurationDetails placementDetails =
//        CreateInstancePoolPlacementConfigurationDetails.builder().primarySubnetId(subnetId).
//            .availabilityDomain(availabilityDomain).secondaryVnicSubnets(Collections.EMPTY_LIST)
//            .build();
//
//    List<CreateInstancePoolPlacementConfigurationDetails> placementConfigurationList =
//        new ArrayList<>();
//    placementConfigurationList.add(placementDetails);

    CreateInstancePoolDetails createInstancePoolDetails = CreateInstancePoolDetails.builder()
        // instancePool dispalyName
      .displayName(desc.qualifiedName())
      .compartmentId(compartmentId).instanceConfigurationId(instanceConfiguration.getId())
      .size(desc.targetSize()).placementConfigurations(desc.getPlacements()).build();

    CreateInstancePoolRequest request = CreateInstancePoolRequest.builder()
      .createInstancePoolDetails(createInstancePoolDetails).build();

    CreateInstancePoolResponse response = client.createInstancePool(request);
    return response.getInstancePool();
  }

  @Override
  public Void operate(List priorOutputs) {
    System.out.println("~~~ cIP " + desc);
    InstancePool ip = createInstancePool();
    System.out.println("~~~ cIP " + ip);
    return null;
  }
}

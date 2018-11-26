package com.netflix.spinnaker.clouddriver.oracle.deploy.converter;

import com.netflix.spinnaker.clouddriver.oracle.deploy.description.BasicOracleDeployDescription;
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.CreateInstancePoolAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;

//TODO replace existing CREATE_SERVER_GROUP
//@OracleOperation(AtomicOperations.CREATE_SERVER_GROUP)
//@Component("createInstancePool")
@SuppressWarnings("rawtypes")
public class CreateInstancePoolAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new CreateInstancePoolAtomicOperation(convertDescription(input));
  }

  @Override
  public BasicOracleDeployDescription convertDescription(Map input) {
    return OracleAtomicOperationConverterHelper.convertDescription(input, this, BasicOracleDeployDescription.class);
  }

//  @Override
//  public CreateInstancePoolDescription convertDescription(Map input) {
//    return OracleAtomicOperationConverterHelper.convertDescription(input, this, CreateInstancePoolDescription.class);
//  }
}

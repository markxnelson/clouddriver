/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.op

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.oracle.model.Details
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.oracle.bmc.loadbalancer.model.Certificate
import com.oracle.bmc.loadbalancer.model.CertificateDetails
import com.oracle.bmc.loadbalancer.model.CreateCertificateDetails
import com.oracle.bmc.loadbalancer.model.BackendDetails
import com.oracle.bmc.loadbalancer.model.BackendSet
import com.oracle.bmc.loadbalancer.model.BackendSetDetails
import com.oracle.bmc.loadbalancer.model.CreateBackendSetDetails
import com.oracle.bmc.loadbalancer.model.CreateListenerDetails
import com.oracle.bmc.loadbalancer.model.CreateLoadBalancerDetails
import com.oracle.bmc.loadbalancer.model.HealthCheckerDetails
import com.oracle.bmc.loadbalancer.model.ListenerDetails
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.model.UpdateBackendSetDetails
import com.oracle.bmc.loadbalancer.model.UpdateListenerDetails
import com.oracle.bmc.loadbalancer.requests.CreateCertificateRequest
import com.oracle.bmc.loadbalancer.requests.CreateListenerRequest
import com.oracle.bmc.loadbalancer.requests.CreateLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.CreateBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.DeleteCertificateRequest
import com.oracle.bmc.loadbalancer.requests.DeleteBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.DeleteListenerRequest
import com.oracle.bmc.loadbalancer.requests.GetLoadBalancerRequest
import com.oracle.bmc.loadbalancer.requests.UpdateBackendSetRequest
import com.oracle.bmc.loadbalancer.requests.UpdateListenerRequest
import com.oracle.bmc.model.BmcException
import groovy.util.logging.Slf4j

@Slf4j
class UpsertOracleLoadBalancerAtomicOperation implements AtomicOperation<Map> {

  private final UpsertLoadBalancerDescription description

  private static final String BASE_PHASE = "UpsertLB" //"CREATE_LOADBALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  UpsertOracleLoadBalancerAtomicOperation(UpsertLoadBalancerDescription description) {
    this.description = description
  }

  UpdateBackendSetDetails toUpdate(BackendSetDetails details, BackendSet existing) {
    UpdateBackendSetDetails.Builder builder = UpdateBackendSetDetails.builder().policy(details.policy)
    if (details.healthChecker) {
      builder.healthChecker(details.healthChecker)
    }
    if (details.sessionPersistenceConfiguration) {
      builder.sessionPersistenceConfiguration(details.sessionPersistenceConfiguration)
    }
    if (details.sslConfiguration) {
      builder.sslConfiguration(details.sslConfiguration)
    }
    List<BackendDetails> backends = existing.backends.collect { Details.of(it) }
    builder.backends(backends)
    return builder.build()
  }

  CreateBackendSetDetails toCreate(BackendSetDetails details, String name) {
    CreateBackendSetDetails.Builder builder = CreateBackendSetDetails.builder().policy(details.policy).name(name)
    if (details.healthChecker) {
      builder.healthChecker(details.healthChecker)
    }
    if (details.sessionPersistenceConfiguration) {
      builder.sessionPersistenceConfiguration(details.sessionPersistenceConfiguration)
    }
    if (details.sslConfiguration) {
      builder.sslConfiguration(details.sslConfiguration)
    }
    return builder.build()
  }

  CreateCertificateDetails toCreate(CertificateDetails details, String name) {
    CreateCertificateDetails.Builder builder = CreateCertificateDetails.builder().certificateName(name)
    if (details.caCertificate) {
      builder.caCertificate(details.caCertificate)
    }
    if (details.publicCertificate) {
      builder.publicCertificate(details.publicCertificate)
    }
    if (details.privateKey) {
      builder.privateKey(details.privateKey)
    }
    if (details.passphrase) {
      builder.passphrase(details.passphrase)
    }
    return builder.build()
  }

  CreateListenerDetails toCreate(ListenerDetails details, String name) {
    CreateListenerDetails.Builder builder = CreateListenerDetails.builder().name(name)
      .protocol(details.protocol).port(details.port)
    if (details.connectionConfiguration) {
      builder.connectionConfiguration(details.connectionConfiguration)
    }
    if (details.defaultBackendSetName) {
      builder.defaultBackendSetName(details.defaultBackendSetName)
    }
    if (details.hostnameNames) {
      builder.hostnameNames(details.hostnameNames)
    }
    if (details.pathRouteSetName) {
      builder.pathRouteSetName(details.pathRouteSetName)
    }
    if (details.sslConfiguration) {
      builder.sslConfiguration(details.sslConfiguration)
    }
    return builder.build()
  }

  UpdateListenerDetails toUpdate(ListenerDetails details) {
    UpdateListenerDetails.Builder builder = UpdateListenerDetails.builder()
      .protocol(details.protocol).port(details.port)
    if (details.connectionConfiguration) {
      builder.connectionConfiguration(details.connectionConfiguration)
    }
    if (details.defaultBackendSetName) {
      builder.defaultBackendSetName(details.defaultBackendSetName)
    }
    if (details.hostnameNames) {
      builder.hostnameNames(details.hostnameNames)
    }
    if (details.pathRouteSetName) {
      builder.pathRouteSetName(details.pathRouteSetName)
    }
    if (details.sslConfiguration) {
      builder.sslConfiguration(details.sslConfiguration)
    }
    return builder.build()
  }
  
  void updateBackendSets(LoadBalancer lb, Task task) {
    lb.backendSets.each { name, existingBackendSet ->
      BackendSetDetails backendSetUpdate = description.backendSets?.get(name);
      if (backendSetUpdate) {
        // Update existing BackendSets
        def rs = description.credentials.loadBalancerClient.updateBackendSet(
          UpdateBackendSetRequest.builder().loadBalancerId(lb.getId()).backendSetName(name)
          .updateBackendSetDetails(toUpdate(backendSetUpdate, existingBackendSet)).build());
        task.updateStatus(BASE_PHASE, "UpdateBackendSetRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
        OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
      } else {
        // Delete backendSet: must have no backend and no listener
        def rs = description.credentials.loadBalancerClient.deleteBackendSet(
          DeleteBackendSetRequest.builder().loadBalancerId(lb.getId()).backendSetName(name).build());
        task.updateStatus(BASE_PHASE, "DeleteBackendSetRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
        OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
      }
    }
    // Add new backendSets
    description.backendSets?.each { name, details -> 
      if (!lb.backendSets.containsKey(name)) {
        def rs = description.credentials.loadBalancerClient.createBackendSet(
          CreateBackendSetRequest.builder().loadBalancerId(description.loadBalancerId)
          .createBackendSetDetails(toCreate(details, name)).build())
        task.updateStatus(BASE_PHASE, "CreateBackendSetRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
        OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
      }
    }
  }
  
  void updateCertificates(LoadBalancer lb, Task task) {
    lb.certificates.each { name, existingCert ->
      CertificateDetails cert = description.certificates?.get(name);
      if (!cert) {
        // Delete certificate: must have no listener using it
        def rs = description.credentials.loadBalancerClient.deleteCertificate(
          DeleteCertificateRequest.builder().loadBalancerId(lb.getId()).certificateName(name).build());
        task.updateStatus(BASE_PHASE, "DeleteCertificateRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
        OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
      }
    }
    // Add new certificate
    description.certificates?.each { name, details ->
      Certificate cert = lb.certificates?.get(name);
      if (!cert) {
        def rs = description.credentials.loadBalancerClient.createCertificate(
          CreateCertificateRequest.builder().loadBalancerId(description.loadBalancerId)
          .createCertificateDetails(toCreate(details, name)).build())
        task.updateStatus(BASE_PHASE, "CreateCertificateRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
        OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
      }
    }
  }
  
  void update(LoadBalancer lb, Task task) {
      task.updateStatus(BASE_PHASE, "Update LB: ${description.qualifiedName()} $lb")
      // Delete Listeners
      lb.listeners.each { name, existingListener ->
        ListenerDetails listenerUpdate = description.listeners?.get(name)
        if (listenerUpdate) {
          // listener could be updated to use new backendSet so do this later
        } else {
          def rs = description.credentials.loadBalancerClient.deleteListener(
            DeleteListenerRequest.builder().loadBalancerId(lb.getId()).listenerName(name).build());
          task.updateStatus(BASE_PHASE, "DeleteListenerRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
          OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
        }
      }
      updateBackendSets(lb, task)
      updateCertificates(lb, task)
      //Update Listeners
      lb.listeners?.each { name, existingListener ->
        ListenerDetails listenerUpdate = description.listeners?.get(name)
        if (listenerUpdate) {
          def rs = description.credentials.loadBalancerClient.updateListener(
            UpdateListenerRequest.builder().loadBalancerId(lb.getId()).listenerName(name)
            .updateListenerDetails(toUpdate(listenerUpdate)).build());
          task.updateStatus(BASE_PHASE, "UpdateListenerRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
          OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
        }
      }
      //Add new Listeners
      description.listeners?.each { name, listener ->
        if (!lb.listeners?.containsKey(name)) {
          def rs = description.credentials.loadBalancerClient.createListener(
            CreateListenerRequest.builder().loadBalancerId(description.loadBalancerId)
            .createListenerDetails(toCreate(listener, name)).build())
          task.updateStatus(BASE_PHASE, "CreateListenerRequest of ${name} submitted - work request id: ${rs.getOpcWorkRequestId()}")
          OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
        }
      }
  } 
  
  void create(Task task) {
    def clusterName = description.qualifiedName()
    task.updateStatus(BASE_PHASE, "Create LB: ${description.qualifiedName()}")
    def lbDetails = CreateLoadBalancerDetails.builder()
        .displayName(clusterName)
        .compartmentId(description.credentials.compartmentId)
        .shapeName(description.shape)
        .subnetIds(description.subnetIds)
    if (description.isPrivate) {
        lbDetails.isPrivate(description.isPrivate)
    }
    if (description.certificates) {
      lbDetails.certificates(description.certificates)
    }
    if (description.backendSets) {
      lbDetails.backendSets(description.backendSets)
    }
    if (description.listeners) {
      lbDetails.listeners(description.listeners)
    }
    def rs = description.credentials.loadBalancerClient.createLoadBalancer(
      CreateLoadBalancerRequest.builder().createLoadBalancerDetails(lbDetails.build()).build())
    task.updateStatus(BASE_PHASE, "Create LB rq submitted - work request id: ${rs.getOpcWorkRequestId()}")
    OracleWorkRequestPoller.poll(rs.getOpcWorkRequestId(), BASE_PHASE, task, description.credentials.loadBalancerClient)
  }
   
  @Override
  Map operate(List priorOutputs) {
    
    System.out.println("~~~~ UpsertLB description " + description);
    
    def task = getTask()
    if (description.loadBalancerId) {
      try {
        LoadBalancer lb = description.credentials.loadBalancerClient.getLoadBalancer(
          GetLoadBalancerRequest.builder().loadBalancerId(description.loadBalancerId).build())?.getLoadBalancer()
        if (lb) {
          update(lb, task)
        } else {
          task.updateStatus BASE_PHASE, "LoadBalancer ${description.loadBalancerId} does not exist."
        }
      } catch (BmcException e) {
        if (e.statusCode == 404) {
          task.updateStatus BASE_PHASE, "LoadBalancer ${description.loadBalancerId} does not exist."
        } else {
          throw e
        }
      }
    } else {
      create(task)
    }
    return [loadBalancers:
              [(description.credentials.region): [name: description.qualifiedName()]]]
  }
}

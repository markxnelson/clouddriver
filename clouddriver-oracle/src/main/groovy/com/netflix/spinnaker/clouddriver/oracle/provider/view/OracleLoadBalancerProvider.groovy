/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.model.OracleInstance
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.model.OracleSubnet
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import groovy.util.logging.Slf4j

@Slf4j
@Component
class OracleLoadBalancerProvider implements LoadBalancerProvider<OracleLoadBalancerDetail> {

  final Cache cacheView
  final ObjectMapper objectMapper
  final String cloudProvider = OracleCloudProvider.ID

  @Autowired
  OracleSubnetProvider oracleSubnetProvider;

  @Autowired
  OracleInstanceProvider instanceProvider

  @Autowired
  OracleLoadBalancerProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  List<LoadBalancerProvider.Item> list() {
    def results = []
    getAll().each { lb ->
      def summary = new OracleLoadBalancerSummary(name: lb.name)
      summary.getOrCreateAccount(lb.account).getOrCreateRegion(lb.region).loadBalancers << lb
      results << summary
    }

    return results
  }

  @Override
  LoadBalancerProvider.Item get(String name) {
    def summary = new OracleLoadBalancerSummary(name: name)
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(name, '*', '*', '*')).each { lb ->
      summary.getOrCreateAccount(lb.account).getOrCreateRegion(lb.region).loadBalancers << lb
    }
    return summary
  }

  @Override
  List<LoadBalancerProvider.Details> byAccountAndRegionAndName(String account, String region, String name) {
    return getAllMatchingKeyPattern(Keys.getLoadBalancerKey(name, '*', region, account))?.toList()
  }

  @Override
  Set<OracleLoadBalancerDetail> getApplicationLoadBalancers(String application) {
    return getAllMatchingKeyPattern(Keys.getLoadBalancerKey("$application*", '*', '*', '*'))
  }

  Set<OracleLoadBalancerDetail> getAll() {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey('*', '*', '*', '*'))
  }

  Set<OracleLoadBalancerDetail> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(Keys.Namespace.LOADBALANCERS.ns, pattern))
  }

  Set<OracleLoadBalancerDetail> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(Keys.Namespace.LOADBALANCERS.ns, identifiers, RelationshipCacheFilter.none())
    Set<OracleLoadBalancerDetail> lbs = data.collect(this.&fromCacheData)
    Set<OracleServerGroup> serverGroups = getServerGroups()
    lbs.each { lb ->
      def sgs = serverGroups.findAll { it.loadBalancerId == lb.id }
      lb.serverGroups = sgs.collect { loadBalancerServerGroup(lb.account, it) }
    }
    return lbs
  }

  Set<OracleServerGroup> getServerGroups() {
    Collection<String> identifiers = cacheView.getIdentifiers(Keys.Namespace.SERVER_GROUPS.ns).findAll { it.startsWith('oracle:') }
    def data = cacheView.getAll(Keys.Namespace.SERVER_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    return data.collect { cacheItem ->
      def sg = objectMapper.convertValue(cacheItem.attributes, OracleServerGroup)
      sg.instances?.each {
        def instance = instanceProvider.getInstance(Keys.parse(cacheItem.id)?.get("account"), "*", it.id)
        if (instance) {
          //TODO see OracleClusterProvider display name with id or privateIp
          //TODO use lifecycleState
          //it.name = it.name + (it.privateIp? '_' + it.privateIp : '')
          it.healthState = instance.healthState
          it.health = instance.health
          if (sg.disabled) {
            it.healthState = HealthState.OutOfService
            it.health[0].state = HealthState.OutOfService.name()
          }
        }
      }?.removeAll {
        def instance = instanceProvider.getInstance(Keys.parse(cacheItem.id)?.get("account"), "*", it.id)
        return instance == null
      }
      return sg
    }
  }

  LoadBalancerServerGroup loadBalancerServerGroup(String acc, OracleServerGroup osg) {
    return new LoadBalancerServerGroup (
      name: osg.name,
      account: acc,
      region: osg.region,
      isDisabled: osg.disabled,
      //TODO detachedInstances;
      instances: osg.instances.collect { ins ->
        new LoadBalancerInstance(id: ins.id, name: ins.name, zone: ins.zone, health: healthOf(ins))
      }
    )
  }

  Map<String, Object> healthOf(OracleInstance ins) {
    def health = [:]
    if (ins.health) {
      ins.health.each { health << it }
    }
    return health;
  }

  OracleLoadBalancerDetail fromCacheData(CacheData cacheData) {
    LoadBalancer loadBalancer = objectMapper.convertValue(cacheData.attributes, LoadBalancer)
    Map<String, String> parts = Keys.parse(cacheData.id)
    Set<OracleSubnet> subnets = loadBalancer.subnetIds?.collect {
      oracleSubnetProvider.getAllMatchingKeyPattern(Keys.getSubnetKey(it, parts.region, parts.account))
    }.flatten();
    return new OracleLoadBalancerDetail(
      id: loadBalancer.id,
      name: loadBalancer.displayName,
      account: parts.account,
      region: parts.region,
      ipAddresses: loadBalancer.ipAddresses,
      certificates: loadBalancer.certificates,
      listeners: loadBalancer.listeners,
      backendSets: loadBalancer.backendSets,
      subnets: subnets,
      timeCreated: loadBalancer.timeCreated.toInstant().toString(),
      serverGroups: [] as Set<LoadBalancerServerGroup>)
  }

  static class OracleLoadBalancerSummary implements LoadBalancerProvider.Item {

    private Map<String, OracleLoadBalancerAccount> mappedAccounts = [:]
    String name

    OracleLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new OracleLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<OracleLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class OracleLoadBalancerAccount implements LoadBalancerProvider.ByAccount {

    private Map<String, OracleLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    OracleLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new OracleLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<OracleLoadBalancerAccountRegion> getByRegions() {
      mappedRegions.values() as List
    }
  }

  static class OracleLoadBalancerAccountRegion implements LoadBalancerProvider.ByRegion {

    String name
    List<OracleLoadBalancerSummary> loadBalancers
  }

  static class OracleLoadBalancerDetail implements LoadBalancerProvider.Details, com.netflix.spinnaker.clouddriver.model.LoadBalancer {

    String account
    String region
    String name
    String type = 'oracle'
    String loadBalancerType = 'oci'
    String cloudProvider = 'oracle'
    String id
    String timeCreated
    Set<LoadBalancerServerGroup> serverGroups = []
    List ipAddresses = []
    Map certificates
    Map listeners
    Map backendSets
    Set<OracleSubnet> subnets
  }

}

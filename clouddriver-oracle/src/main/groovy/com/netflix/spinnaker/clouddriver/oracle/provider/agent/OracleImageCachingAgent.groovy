/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.core.model.Image
import com.oracle.bmc.core.model.Shape
import com.oracle.bmc.core.requests.ListImagesRequest
import com.oracle.bmc.core.requests.ListShapesRequest
import groovy.util.logging.Slf4j

@Slf4j
class OracleImageCachingAgent extends AbstractOracleCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.IMAGES.ns)
  ] as Set

  Map<String, List<Shape>> cache = [:]

  OracleImageCachingAgent(String clouddriverUserAgentApplicationName,
                              OracleNamedAccountCredentials credentials,
                              ObjectMapper objectMapper) {
    super(objectMapper, credentials, clouddriverUserAgentApplicationName)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Image> imageList = loadImages()
    return buildCacheResult(imageList)
  }

  List<Image> loadImages() {
    def response = credentials.computeClient.listImages(ListImagesRequest.builder()
      .compartmentId(credentials.compartmentId)
      .build())
    return response.items
  }

  String imageKey(String compartmentId, String imageId) {
    compartmentId + "-" + imageId
  }
  private CacheResult buildCacheResult(List<Image> imageList) {
    log.info("Describing cached items in ${agentType}")
    List<CacheData> data = imageList.collect { Image image ->
      if (image.lifecycleState != Image.LifecycleState.Available) {
        return null
      }
      Map<String, Object> attributes = objectMapper.convertValue(image, ATTRIBUTES)
      String imageKey = imageKey(credentials.compartmentId, image.id)
      List<Shape> unique = cache.get(imageKey)
      if (unique == null) {
        def shapesResponse = credentials.computeClient.listShapes(ListShapesRequest.builder()
          .compartmentId(credentials.compartmentId)
          .imageId(image.id)
          .build())
        // Shapes are per-AD so we get multiple copies of the compatible shapes
        unique = shapesResponse?.items?.unique { it.shape }
        cache.put(imageKey, unique)
      }
      attributes.put("compatibleShapes", unique.collect { it.shape })

      new DefaultCacheData(
        Keys.getImageKey(credentials.name, credentials.region, image.id),
        attributes,
        [:]
      )
    }
    data.removeAll { it == null }
    def cacheData = [(Keys.Namespace.IMAGES.ns): data]
    log.info("Caching ${data.size()} items in ${agentType}")
    return new DefaultCacheResult(cacheData, [:])
  }
}

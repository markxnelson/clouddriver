/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ResourceUtil {
  private static ObjectMapper MAPPER = new ObjectMapper();

  public static Object read(String fileName) throws URISyntaxException, IOException {
    byte[] json = Files.readAllBytes(Paths.get(ResourceUtil.class.getResource("/desc/" + fileName).toURI()));
    List<Map<String, Object>> data = MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
    return data.get(0);
  }
}

/*
 * Copyright (c) 2017, 2018, Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.artifacts.oracle;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;

public class OracleArtifactCredentialsTest {
  @Test
  public void testOracleArtifactCredentialsDownload() throws IOException, GeneralSecurityException {
    OracleArtifactAccount account = new OracleArtifactAccount();
    account.setNamespace("odx-pipelines");
    account.setRegion("us-ashburn-1");
    account.setUserId("ocid1.user.oc1..aaaaaaaav55sqkvw5axwb2rw3orbsbi7hsqh4bx3ptqz2pekbrbchbbfxkra");
    account.setFingerprint("49:77:32:a6:09:e5:ba:3a:62:2e:15:f3:d8:21:0b:ca");
    account.setFingerprint("12:22:b1:6a:da:24:53:93:6e:30:81:c4:c2:2b:f5:ad");
    account.setSshPrivateKeyFilePath("//Users/guzhang/oci/oci_api_key.pem");
    account.setSshPrivateKeyFilePath("//Users/guzhang/oci/gz_api_key_passphrase.pem");
    account.setPrivateKeyPassphrase("welcome");
    account.setTenancyId("ocid1.tenancy.oc1..aaaaaaaax35wv5a7d7xtbdi62tz25kw2so3mg2oiiexredhsxq27rfiqilva");

    OracleArtifactCredentials credentials = new OracleArtifactCredentials("random", account);

    Artifact artifact = new Artifact();
    artifact.setReference("oci://gz-bucket/helm/values.yaml");

    String content = new String(readBytes(credentials.download(artifact)));
    System.out.println(content);

    assertThat(content.contains("sauron:"));
    assertThat(content.contains("meteorUsername: bWV0ZW9y"));
    assertThat(content.contains("meteorPassword: dSZFRDMlRGZIRw=="));
  }

  private byte[] readBytes( InputStream stream ) throws IOException {
    byte[] buffer = new byte[1024];
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    boolean error = false;
    try {
      int numRead = 0;
      while ((numRead = stream.read(buffer)) > -1) {
        output.write(buffer, 0, numRead);
      }
    } catch (IOException e) {
      error = true; // this error should be thrown, even if there is an error closing stream
      throw e;
    } catch (RuntimeException e) {
      error = true; // this error should be thrown, even if there is an error closing stream
      throw e;
    } finally {
      try {
        stream.close();
      } catch (IOException e) {
        if (!error) throw e;
      }
    }
    output.flush();
    return output.toByteArray();
  }
}

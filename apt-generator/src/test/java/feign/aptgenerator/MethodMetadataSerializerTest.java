/**
 * Copyright 2012-2019 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.aptgenerator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import com.atlassian.hamcrest.DeepIsEqual;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import example.github.Repository;
import feign.CollectionFormat;
import feign.MethodMetadata;
import feign.Request.HttpMethod;
import feign.apt.runtime.ParameterizedType;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;


public class MethodMetadataSerializerTest {

  @Test
  public void githubGetRepos() {
    final MethodMetadata md = new MethodMetadata();
    md.returnType(new ParameterizedType(List.class, null, Repository.class));
    md.configKey("GitHub#repos(String)");

    md.template().method(HttpMethod.GET);
    md.template().uri("/users/{username}/repos?sort=full_name");
    md.template().decodeSlash(true);
    md.template().collectionFormat(CollectionFormat.EXPLODED);

    md.indexToName().put(0, Arrays.asList("username"));
    md.indexToEncoded().put(0, false);

    final StringBuilder result = MethodMetadataSerializer.toJavaCode(md);
    assertEquals(result.toString(),
        "    final MethodMetadata md = new MethodMetadata();\n" +
            "    md.returnType(new feign.apt.runtime.ParameterizedType(java.util.List.class, null, example.github.Repository.class));\n"
            +
        "    md.configKey(\"GitHub#repos(String)\");\n" +
        "\n" +
            "    md.template().method(feign.Request.HttpMethod.GET);\n" +
        "    md.template().uri(\"/users/{username}/repos?sort=full_name\", false);\n" +
        "    md.template().decodeSlash(true);\n" +
        "    md.template().collectionFormat(CollectionFormat.EXPLODED);\n" +
        "\n" +
        "    md.indexToName().put(0, Arrays.asList(\"username\"));\n" +
            "    md.indexToEncoded().put(0, false);\n");

    final Binding binding = new Binding();
    binding.setVariable("foo", new Integer(2));
    final GroovyShell shell = new GroovyShell(binding);

    final MethodMetadata resultMetadata = (MethodMetadata) shell.evaluate(""
        + "import feign.*;\n"
        + result.toString()
        + "\nreturn md;");

    assertThat(resultMetadata, DeepIsEqual.deeplyEqualTo(md));
  }

}

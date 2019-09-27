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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import com.atlassian.hamcrest.DeepIsEqual;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import example.github.GitHubExample.GitHub;
import feign.Contract;
import feign.DefaultContractTest;
import feign.MethodMetadata;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;


/**
 * Check if the generated java code generates a new {@link MethodMetadata} that is equal to source
 * {@link MethodMetadata}
 */
@RunWith(Parameterized.class)
public class MethodMetadataSerializerRepeatTest {

  private static final GroovyShell shell = new GroovyShell(new Binding());

  @Parameters(name = "{0}")
  public static List<Object[]> methods() {
    return Arrays.asList(
          GitHub.class,
        // TODO ideally we need to test all interfaces from DefaultContractTest
          DefaultContractTest.BodyWithoutParameters.class,
        // expanders not supported
        // DefaultContractTest.CustomExpander.class,
          DefaultContractTest.CustomMethod.class,
          DefaultContractTest.DefaultMethodOnInterface.class,
          DefaultContractTest.FormParams.class,
          DefaultContractTest.HeaderParams.class,
          DefaultContractTest.HeaderParamsNotAtStart.class,
          DefaultContractTest.HeadersContainsWhitespaces.class,
          DefaultContractTest.HeadersOnType.class,
          DefaultContractTest.Methods.class
        )
        .stream()
        // create methodmetadata using default reflective code
        .map(new Contract.Default()::parseAndValidatateMetadata)
        .flatMap(List::stream)
        .map(md -> new Object[] {md.configKey(), md})
        .collect(Collectors.toList());
  }

  private final MethodMetadata md;

  public MethodMetadataSerializerRepeatTest(String configKey, MethodMetadata md) {
    this.md = md;
  }

  @Test
  public void serializedSameAsReflective() {

    final StringBuilder result = MethodMetadataSerializer.toJavaCode(md);

    final MethodMetadata resultMetadata = (MethodMetadata) shell.evaluate(""
        + "import feign.*;\n"
        + result.toString()
        + "\nreturn md;");

    assertThat(resultMetadata, not(sameInstance(md)));
    assertThat(resultMetadata, DeepIsEqual.deeplyEqualTo(md));
    // compare transient fields too
    assertThat(resultMetadata.indexToExpander(), equalTo(md.indexToExpander()));
    assertThat(resultMetadata.returnType(), equalTo(md.returnType()));
    assertThat(resultMetadata.bodyType(), equalTo(md.bodyType()));

  }

}

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

import org.apache.commons.text.StringEscapeUtils;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Collectors;
import feign.MethodMetadata;

public class MethodMetadataSerializer {

  public static StringBuilder toJavaCode(MethodMetadata method) {
    final StringBuilder sb = new StringBuilder();

    sb.append("    final MethodMetadata md = new MethodMetadata();").append("\n");
    sb.append("    md.returnType(" + toJavaCode(method.returnType()) + ");").append("\n");
    sb.append("    md.configKey(\"" + method.configKey() + "\");").append("\n");
    if (method.bodyIndex() != null) {
      sb.append("    md.bodyIndex(" + method.bodyIndex() + ");").append("\n");
    }
    sb.append("\n");
    sb.append(
        "    md.template().method(feign.Request.HttpMethod." + method.template().method() + ");")
        .append("\n");
    sb.append("    md.template().uri(\"" + method.template().uri() + "\", "+method.template().uriAppend()+");")
        .append("\n");
    sb.append("    md.template().decodeSlash(" + method.template().decodeSlash() + ");")
        .append("\n");
    sb.append("    md.template().collectionFormat(CollectionFormat."
        + method.template().collectionFormat().name() + ");")
        .append("\n");
    method.template().headers().forEach((headerName, value) -> sb
        .append("    md.template().header(\"" + headerName + "\", "
            + value.stream().collect(Collectors.joining("\",\"", "\"", "\"")) + ");"));
    if (method.template().requestBody().asBytes() != null) {
      sb.append("    md.template().body(\""
          + method.template().requestBody().asString()
          + "\");")
          .append("\n");
    }
    if (method.template().requestBody().bodyTemplate() != null) {
      sb.append("    md.template().body(feign.Request.Body.bodyTemplate(\""
          + StringEscapeUtils.escapeJava(method.template().requestBody().bodyTemplate())
          + "\", java.nio.charset.Charset.forName(\""
          + method.template().requestBody().encoding().name() + "\")"
          + "));")
          .append("\n");
    }


    sb.append("\n");
    method.formParams().forEach(formParam -> sb
        .append("    md.formParams().add(\"" + formParam + "\");"));

    method.indexToName().forEach((index, valueList) -> sb
        .append("    md.indexToName().put(" + index + ", Arrays.asList("
            + valueList.stream().collect(Collectors.joining("\",\"", "\"", "\"")) + "));")
        .append("\n"));
    if (method.indexToExpander() != null) {
      method.indexToExpander().forEach((index, expander) -> sb
          .append("    md.indexToExpander().put(" + index + ", new " + expander.getClass().getName()            + "());")
          .append("\n"));
    }
    method.indexToEncoded().forEach((index, encoded) -> sb
        .append("    md.indexToEncoded().put(" + index + ", " + encoded + ");")
        .append("\n"));
    if (method.bodyType() != null) {
      sb.append("    md.bodyType(" + toJavaCode(method.bodyType()) + ");").append("\n");
    }

    return sb;
  }

  private static String toJavaCode(Type type) {
    if (type == null) {
      return null;
    }

    if (type instanceof Class) {
      return ((Class) type).getName() + ".class";
    }

    if (type instanceof ParameterizedType) {
      return "new feign.apt.runtime.ParameterizedType(" +
          toJavaCode(((ParameterizedType) type).getRawType()) + ", " +
          toJavaCode(((ParameterizedType) type).getOwnerType()) + ", " +
          Arrays.stream(((ParameterizedType) type).getActualTypeArguments())
              .map(MethodMetadataSerializer::toJavaCode)
              .collect(Collectors.joining(","))
          + ")";
    }
    throw new RuntimeException("not implemented " + type.getClass());
  }

}

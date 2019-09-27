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

import static feign.Util.checkState;
import static feign.Util.emptyToNull;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import feign.Contract;
import feign.MethodMetadata;
import feign.Param;

@SupportedAnnotationTypes({
    "feign.RequestLine"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class ContractAPT extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    System.out.println(annotations);
    System.out.println(roundEnv);

    final Map<TypeElement, List<ExecutableElement>> clientsToGenerate = annotations.stream()
        .map(roundEnv::getElementsAnnotatedWith)
        .flatMap(Set::stream)
        .map(ExecutableElement.class::cast)
        .collect(Collectors.toMap(
            annotatedMethod -> TypeElement.class.cast(annotatedMethod.getEnclosingElement()),
            ImmutableList::of,
            (list1, list2) -> ImmutableList.<ExecutableElement>builder()
                .addAll(list1)
                .addAll(list2)
                .build()));

    System.out.println("Count: " + clientsToGenerate.size());
    System.out.println("clientsToGenerate: " + clientsToGenerate);

    final Contract.Default contract = new Contract.Default();
    contract.registerParameterAnnotation(Param.class, (paramAnnotation, data, paramIndex) -> {
      final String name = paramAnnotation.value();
      checkState(emptyToNull(name) != null, "Param annotation was empty on param %s.",
          paramIndex);
      contract.nameParam(data, name, paramIndex);
      // FIXME exception "Attempt to access Class object for TypeMirror"
      // Class<? extends Param.Expander> expander = paramAnnotation.expander();
      // if (expander != Param.ToStringExpander.class) {
      // data.indexToExpanderClass().put(paramIndex, expander);
      // }
      data.indexToEncoded().put(paramIndex, paramAnnotation.encoded());
      if (!data.template().hasRequestVariable(name)) {
        data.formParams().add(name);
      }
      return true;
    });

    clientsToGenerate.forEach((type, methods) -> {
      try {
        final String jPackage = readPackage(type);
        final JavaFileObject builderFile = processingEnv.getFiler()
            .createSourceFile(type.getSimpleName() + "Factory");
        final StringBuilder writer = new StringBuilder();
        writer.append("package " + jPackage + ";").append("\n");
        writer.append("import feign.*;").append("\n");
        writer.append("public class " + type.getSimpleName() + "Factory").append("\n");
        writer.append("{").append("\n");

        final List<MethodMetadata> methodMetadatas =
            new APTContractVisitor().parseAndValidateMetadata(type, contract);

        methodMetadatas.stream()
            .peek(
                method -> System.out.println("Generating metadata for method" + method.configKey()))
            .forEach(method -> {
              final String metadataFieldName =
                  "__" + method.configKey().replaceAll("\\W+", "_") + "_metadata";
          writer
                  .append(
                      "private static final MethodMetadata " + metadataFieldName + ";")
                  .append("\n");
              writer
                  .append("static { ").append("\n")
                  .append(MethodMetadataSerializer.toJavaCode(method)).append("\n")
                  .append(metadataFieldName + " = md;").append("\n")
                  .append("}").append("\n");
        });

        writer.append("}").append("\n");

        System.out.println(writer);

        builderFile.openWriter().append(writer).close();
      } catch (final Exception e) {
        e.printStackTrace();
        processingEnv.getMessager().printMessage(Kind.ERROR,
            "Unable to generate factory for " + type);
      }
    });

    if (!clientsToGenerate.isEmpty()) {
      try {
        final JavaFileObject builderFile = processingEnv.getFiler()
            .createSourceFile("feign.apt.runtime.ParameterizedType");
        ByteStreams.copy(
            getClass().getResourceAsStream("/feign/apt/runtime/ParameterizedType.java"),
            builderFile.openOutputStream());
      } catch (final Exception e) {
        e.printStackTrace();
        processingEnv.getMessager().printMessage(Kind.ERROR,
            "Unable to write ParameterizedType.java");
      }
    }

    return true;
  }



  private Type toJavaType(TypeMirror type) {
    outType(type.getClass());
    if (type instanceof WildcardType) {

    }
    return Object.class;
  }

  private void outType(Class<?> class1) {
    if (Object.class.equals(class1) || class1 == null) {
      return;
    }
    System.out.println(class1);
    outType(class1.getSuperclass());
    Arrays.stream(class1.getInterfaces()).forEach(this::outType);
  }



  private String readPackage(Element type) {
    if (type.getKind() == ElementKind.PACKAGE) {
      return type.getSimpleName().toString();
    }

    if (type.getKind() == ElementKind.CLASS
        || type.getKind() == ElementKind.INTERFACE) {
      return readPackage(type.getEnclosingElement());
    }

    return null;
  }

}

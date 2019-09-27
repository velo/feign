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
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import javax.lang.model.element.*;
import feign.DeclarativeContract;
import feign.DeclarativeContract.AnnotationProcessor;
import feign.DeclarativeContract.ParameterAnnotationProcessor;
import feign.MethodMetadata;

/**
 * Defines what annotations and values are valid on interfaces.
 */
public class APTContractVisitor {

  public List<MethodMetadata> parseAndValidateMetadata(TypeElement targetType,
                                                       DeclarativeContract processorsSource) {
    checkState(targetType.getTypeParameters().size() == 0, "Parameterized types unsupported: %s",
        targetType.getSimpleName());
    checkState(targetType.getInterfaces().size() <= 1, "Only single inheritance supported: %s",
        targetType.getSimpleName());

    final Map<String, MethodMetadata> result = new LinkedHashMap<String, MethodMetadata>();
    for (final Element element : targetType.getEnclosedElements()) {
      if (element instanceof ExecutableElement) {
        final ExecutableElement method = (ExecutableElement) element;
        if (method.getModifiers().contains(Modifier.STATIC) ||
            method.isDefault()) {
          continue;
        }
        final MethodMetadata metadata =
            parseAndValidateMetadata(targetType, method, processorsSource);
        checkState(!result.containsKey(metadata.configKey()), "Overrides unsupported: %s",
            metadata.configKey());
        result.put(metadata.configKey(), metadata);
      }
    }
    return new ArrayList<>(result.values());
  }


  /**
   * Called indirectly by {@link #parseAndValidateMetadata(Class)}.
   */
  protected MethodMetadata parseAndValidateMetadata(TypeElement targetType,
                                                    ExecutableElement method,
                                                    DeclarativeContract processorsSource) {
    final MethodMetadata data = new MethodMetadata();
    // TODO create warping type data.returnType(method.getReturnType());
    data.configKey(configKey(targetType, method));

    // TODO if (targetType.getInterfaces().size() == 1) {
    // processAnnotationOnClass(data, targetType.getInterfaces().get(0), processorsSource);
    // }
    processAnnotationOnClass(data, targetType, processorsSource);

    processAnnotationOnMethod(data, method, processorsSource);
    checkState(data.template().method() != null,
        "Method %s not annotated with HTTP method type (ex. GET, POST)",
        data.configKey());
    final List<? extends VariableElement> parameterTypes = method.getParameters();

    final int count = parameterTypes.size();
    for (int i = 0; i < count; i++) {
      boolean isHttpAnnotation = false;
      if (!parameterTypes.get(i).getAnnotationMirrors().isEmpty()) {
        isHttpAnnotation =
            processAnnotationsOnParameter(data, parameterTypes.get(i), i, processorsSource);
      }
      if (
      // TODO this probably do not work as intended
      parameterTypes.get(i).asType().toString() == URI.class.getName()) {
        data.urlIndex(i);
      } else if (!isHttpAnnotation
      // TODO && parameterTypes[i] != Request.Options.class
      ) {
        checkState(data.formParams().isEmpty(),
            "Body parameters cannot be used with form parameters.");
        checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s", method);
        data.bodyIndex(i);
        // data.bodyType(Types.resolve(targetType, targetType, genericParameterTypes[i]));
      }
    }

    if (data.headerMapIndex() != null) {
      // TODO how to check if one param is a Map on APT?
      // checkMapString("HeaderMap", parameterTypes[data.headerMapIndex()],
      // genericParameterTypes[data.headerMapIndex()]);
    }

    if (data.queryMapIndex() != null) {
      // TODO how to check if one param is a Map on APT?
      // if (Map.class.isAssignableFrom(parameterTypes[data.queryMapIndex()])) {
      // checkMapKeys("QueryMap", genericParameterTypes[data.queryMapIndex()]);
      // }
    }

    return data;
  }

  private String configKey(TypeElement targetType, ExecutableElement method) {
    final StringBuilder builder = new StringBuilder();
    builder.append(targetType.getSimpleName());
    builder.append('#').append(method.getSimpleName()).append('(');
    for (final TypeParameterElement param : method.getTypeParameters()) {
      builder.append(param.getSimpleName()).append(',');
    }
    if (method.getTypeParameters().size() > 0) {
      builder.deleteCharAt(builder.length() - 1);
    }
    return builder.append(')').toString();
  }

  private static void checkMapString(String name, Class<?> type, Type genericType) {
    checkState(Map.class.isAssignableFrom(type),
        "%s parameter must be a Map: %s", name, type);
    checkMapKeys(name, genericType);
  }

  private static void checkMapKeys(String name, Type genericType) {
    Class<?> keyClass = null;

    // assume our type parameterized
    if (ParameterizedType.class.isAssignableFrom(genericType.getClass())) {
      final Type[] parameterTypes = ((ParameterizedType) genericType).getActualTypeArguments();
      keyClass = (Class<?>) parameterTypes[0];
    } else if (genericType instanceof Class<?>) {
      // raw class, type parameters cannot be inferred directly, but we can scan any extended
      // interfaces looking for any explict types
      final Type[] interfaces = ((Class) genericType).getGenericInterfaces();
      if (interfaces != null) {
        for (final Type extended : interfaces) {
          if (ParameterizedType.class.isAssignableFrom(extended.getClass())) {
            // use the first extended interface we find.
            final Type[] parameterTypes = ((ParameterizedType) extended).getActualTypeArguments();
            keyClass = (Class<?>) parameterTypes[0];
            break;
          }
        }
      }
    }

    if (keyClass != null) {
      checkState(String.class.equals(keyClass),
          "%s key must be a String: %s", name, keyClass.getSimpleName());
    }
  }

  /**
   * Called by parseAndValidateMetadata twice, first on the declaring class, then on the target type
   * (unless they are the same).
   *
   * @param data metadata collected so far relating to the current java method.
   * @param processorsSource
   * @param clz the class to process
   */
  protected void processAnnotationOnClass(MethodMetadata data,
                                          TypeElement targetType,
                                          DeclarativeContract processorsSource) {
    final List<GuardedAnnotationProcessor> annotations =
        processorsSource.getClassAnnotationProcessors();
    annotations.forEach((type, processor) -> {
      final Annotation annotation = targetType.getAnnotation(type);
      if (annotation != null) {
        processor.process(annotation, data);
      }
    });
  }

  /**
   * @param data metadata collected so far relating to the current java method.
   * @param method method currently being processed.
   * @param processorsSource
   */
  private void processAnnotationOnMethod(MethodMetadata data,
                                         ExecutableElement method,
                                         DeclarativeContract processorsSource) {
    final Map<Class<Annotation>, AnnotationProcessor<Annotation>> annotations =
        processorsSource.getMethodAnnotationProcessors();
    annotations.forEach((type, processor) -> {
      final Annotation annotation = method.getAnnotation(type);
      if (annotation != null) {
        processor.process(annotation, data);
      }
    });
  }

  /**
   * @param data metadata collected so far relating to the current java method.
   * @param annotations annotations present on the current parameter annotation.
   * @param paramIndex if you find a name in {@code annotations}, call
   *        {@link #nameParam(MethodMetadata, String, int)} with this as the last parameter.
   * @return true if you called {@link #nameParam(MethodMetadata, String, int)} after finding an
   *         http-relevant annotation.
   */
  private boolean processAnnotationsOnParameter(MethodMetadata data,
                                                VariableElement parameter,
                                                int paramIndex,
                                                DeclarativeContract processorsSource) {
    final Map<Class<Annotation>, ParameterAnnotationProcessor<Annotation>> annotations =
        processorsSource.getParameterAnnotationProcessors();

    return annotations.entrySet()
        .stream()
        .map(entry -> {
          final Annotation annotation = parameter.getAnnotation(entry.getKey());
          if (annotation != null) {
            return entry.getValue().process(annotation, data, paramIndex);
          }
          return false;
        })
        .collect(Collectors.reducing(Boolean::logicalOr))
        .orElse(false);
  }

  /**
   * links a parameter name to its index in the method signature.
   */
  protected void nameParam(MethodMetadata data, String name, int i) {
    final Collection<String> names =
        data.indexToName().containsKey(i) ? data.indexToName().get(i) : new ArrayList<String>();
    names.add(name);
    data.indexToName().put(i, names);
  }

}

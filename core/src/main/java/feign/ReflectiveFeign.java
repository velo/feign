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
package feign;

import static feign.Util.checkArgument;
import static feign.Util.checkNotNull;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.Map.Entry;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Param.Expander;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.template.UriUtils;

public class ReflectiveFeign extends Feign {

  private final ParseHandlersByName targetToHandlersByName;
  private final InvocationHandlerFactory factory;
  private final QueryMapEncoder queryMapEncoder;

  ReflectiveFeign(ParseHandlersByName targetToHandlersByName, InvocationHandlerFactory factory,
      QueryMapEncoder queryMapEncoder) {
    this.targetToHandlersByName = targetToHandlersByName;
    this.factory = factory;
    this.queryMapEncoder = queryMapEncoder;
  }

  /**
   * creates an api binding to the {@code target}. As this invokes reflection, care should be taken
   * to cache the result.
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T newInstance(Target<T> target) {
    final Map<String, MethodHandler> nameToHandler = targetToHandlersByName.apply(target);
    final Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<Method, MethodHandler>();
    final List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<DefaultMethodHandler>();

    for (final Method method : target.type().getMethods()) {
      if (method.getDeclaringClass() == Object.class) {
        continue;
      } else if (Util.isDefault(method)) {
        final DefaultMethodHandler handler = new DefaultMethodHandler(method);
        defaultMethodHandlers.add(handler);
        methodToHandler.put(method, handler);
      } else {
        methodToHandler.put(method, nameToHandler.get(Feign.configKey(target.type(), method)));
      }
    }
    final InvocationHandler handler = factory.create(target, methodToHandler);
    final T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(),
        new Class<?>[] {target.type()}, handler);

    for (final DefaultMethodHandler defaultMethodHandler : defaultMethodHandlers) {
      defaultMethodHandler.bindTo(proxy);
    }
    return proxy;
  }

  static class FeignInvocationHandler implements InvocationHandler {

    private final Target target;
    private final Map<Method, MethodHandler> dispatch;

    FeignInvocationHandler(Target target, Map<Method, MethodHandler> dispatch) {
      this.target = checkNotNull(target, "target");
      this.dispatch = checkNotNull(dispatch, "dispatch for %s", target);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("equals".equals(method.getName())) {
        try {
          final Object otherHandler =
              args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
          return equals(otherHandler);
        } catch (final IllegalArgumentException e) {
          return false;
        }
      } else if ("hashCode".equals(method.getName())) {
        return hashCode();
      } else if ("toString".equals(method.getName())) {
        return toString();
      }

      return dispatch.get(method).invoke(args);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof FeignInvocationHandler) {
        final FeignInvocationHandler other = (FeignInvocationHandler) obj;
        return target.equals(other.target);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return target.hashCode();
    }

    @Override
    public String toString() {
      return target.toString();
    }
  }

  static final class ParseHandlersByName {

    private final Contract contract;
    private final FeignConfig feignConfig;
    private final SynchronousMethodHandler.Factory factory;

    ParseHandlersByName(
        Contract contract,
        FeignConfig feignConfig,
        SynchronousMethodHandler.Factory factory) {
      this.contract = checkNotNull(contract, "contract");
      this.feignConfig = checkNotNull(feignConfig, "feignConfig");
      this.factory = checkNotNull(factory, "factory");
    }

    public Map<String, MethodHandler> apply(Target key) {
      final List<MethodMetadata> metadata = contract.parseAndValidatateMetadata(key.type());
      final Map<String, MethodHandler> result = new LinkedHashMap<String, MethodHandler>();
      for (final MethodMetadata md : metadata) {
        final RequestTemplate.Factory buildTemplate = from(md, feignConfig);
        result.put(md.configKey(),
            factory.create(key, md, buildTemplate, feignConfig.options, feignConfig.decoder,
                feignConfig.errorDecoder));
      }
      return result;
    }

  }

  public static RequestTemplate.Factory from(MethodMetadata md, FeignConfig feignConfig) {

    if (!md.formParams().isEmpty() && md.template().bodyTemplate() == null) {
      return new BuildFormEncodedTemplateFromArgs(md, feignConfig.encoder,
          feignConfig.queryMapEncoder);
    } else if (md.bodyIndex() != null) {
      return new BuildEncodedTemplateFromArgs(md, feignConfig.encoder,
          feignConfig.queryMapEncoder);
    } else {
      return new BuildTemplateByResolvingArgs(md, feignConfig.queryMapEncoder);
    }
  }

  private static class BuildTemplateByResolvingArgs implements RequestTemplate.Factory {

    private final QueryMapEncoder queryMapEncoder;

    protected final MethodMetadata metadata;
    private final Map<Integer, Expander> indexToExpander = new LinkedHashMap<Integer, Expander>();

    private BuildTemplateByResolvingArgs(MethodMetadata metadata, QueryMapEncoder queryMapEncoder) {
      this.metadata = metadata;
      this.queryMapEncoder = queryMapEncoder;
      if (metadata.indexToExpander() != null) {
        indexToExpander.putAll(metadata.indexToExpander());
        return;
      }
      if (metadata.indexToExpanderClass().isEmpty()) {
        return;
      }
      for (final Entry<Integer, Class<? extends Expander>> indexToExpanderClass : metadata
          .indexToExpanderClass().entrySet()) {
        try {
          indexToExpander
              .put(indexToExpanderClass.getKey(), indexToExpanderClass.getValue().newInstance());
        } catch (final InstantiationException e) {
          throw new IllegalStateException(e);
        } catch (final IllegalAccessException e) {
          throw new IllegalStateException(e);
        }
      }
    }

    @Override
    public RequestTemplate create(Object[] argv) {
      final RequestTemplate mutable = RequestTemplate.from(metadata.template());
      if (metadata.urlIndex() != null) {
        final int urlIndex = metadata.urlIndex();
        checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
        mutable.target(String.valueOf(argv[urlIndex]));
      }
      final Map<String, Object> varBuilder = new LinkedHashMap<String, Object>();
      for (final Entry<Integer, Collection<String>> entry : metadata.indexToName().entrySet()) {
        final int i = entry.getKey();
        Object value = argv[entry.getKey()];
        if (value != null) { // Null values are skipped.
          if (indexToExpander.containsKey(i)) {
            value = expandElements(indexToExpander.get(i), value);
          }
          for (final String name : entry.getValue()) {
            varBuilder.put(name, value);
          }
        }
      }

      RequestTemplate template = resolve(argv, mutable, varBuilder);
      if (metadata.queryMapIndex() != null) {
        // add query map parameters after initial resolve so that they take
        // precedence over any predefined values
        final Object value = argv[metadata.queryMapIndex()];
        final Map<String, Object> queryMap = toQueryMap(value);
        template = addQueryMapQueryParameters(queryMap, template);
      }

      if (metadata.headerMapIndex() != null) {
        template =
            addHeaderMapHeaders((Map<String, Object>) argv[metadata.headerMapIndex()], template);
      }

      return template;
    }

    private Map<String, Object> toQueryMap(Object value) {
      if (value instanceof Map) {
        return (Map<String, Object>) value;
      }
      try {
        return queryMapEncoder.encode(value);
      } catch (final EncodeException e) {
        throw new IllegalStateException(e);
      }
    }

    private Object expandElements(Expander expander, Object value) {
      if (value instanceof Iterable) {
        return expandIterable(expander, (Iterable) value);
      }
      return expander.expand(value);
    }

    private List<String> expandIterable(Expander expander, Iterable value) {
      final List<String> values = new ArrayList<String>();
      for (final Object element : value) {
        if (element != null) {
          values.add(expander.expand(element));
        }
      }
      return values;
    }

    @SuppressWarnings("unchecked")
    private RequestTemplate addHeaderMapHeaders(Map<String, Object> headerMap,
                                                RequestTemplate mutable) {
      for (final Entry<String, Object> currEntry : headerMap.entrySet()) {
        final Collection<String> values = new ArrayList<String>();

        final Object currValue = currEntry.getValue();
        if (currValue instanceof Iterable<?>) {
          final Iterator<?> iter = ((Iterable<?>) currValue).iterator();
          while (iter.hasNext()) {
            final Object nextObject = iter.next();
            values.add(nextObject == null ? null : nextObject.toString());
          }
        } else {
          values.add(currValue == null ? null : currValue.toString());
        }

        mutable.header(currEntry.getKey(), values);
      }
      return mutable;
    }

    @SuppressWarnings("unchecked")
    private RequestTemplate addQueryMapQueryParameters(Map<String, Object> queryMap,
                                                       RequestTemplate mutable) {
      for (final Entry<String, Object> currEntry : queryMap.entrySet()) {
        final Collection<String> values = new ArrayList<String>();

        final boolean encoded = metadata.queryMapEncoded();
        final Object currValue = currEntry.getValue();
        if (currValue instanceof Iterable<?>) {
          final Iterator<?> iter = ((Iterable<?>) currValue).iterator();
          while (iter.hasNext()) {
            final Object nextObject = iter.next();
            values.add(nextObject == null ? null
                : encoded ? nextObject.toString()
                    : UriUtils.encode(nextObject.toString()));
          }
        } else {
          values.add(currValue == null ? null
              : encoded ? currValue.toString() : UriUtils.encode(currValue.toString()));
        }

        mutable.query(encoded ? currEntry.getKey() : UriUtils.encode(currEntry.getKey()), values);
      }
      return mutable;
    }

    protected RequestTemplate resolve(Object[] argv,
                                      RequestTemplate mutable,
                                      Map<String, Object> variables) {
      return mutable.resolve(variables);
    }
  }

  private static class BuildFormEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

    private final Encoder encoder;

    private BuildFormEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder,
        QueryMapEncoder queryMapEncoder) {
      super(metadata, queryMapEncoder);
      this.encoder = encoder;
    }

    @Override
    protected RequestTemplate resolve(Object[] argv,
                                      RequestTemplate mutable,
                                      Map<String, Object> variables) {
      final Map<String, Object> formVariables = new LinkedHashMap<String, Object>();
      for (final Entry<String, Object> entry : variables.entrySet()) {
        if (metadata.formParams().contains(entry.getKey())) {
          formVariables.put(entry.getKey(), entry.getValue());
        }
      }
      try {
        encoder.encode(formVariables, Encoder.MAP_STRING_WILDCARD, mutable);
      } catch (final EncodeException e) {
        throw e;
      } catch (final RuntimeException e) {
        throw new EncodeException(e.getMessage(), e);
      }
      return super.resolve(argv, mutable, variables);
    }
  }

  private static class BuildEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

    private final Encoder encoder;

    private BuildEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder,
        QueryMapEncoder queryMapEncoder) {
      super(metadata, queryMapEncoder);
      this.encoder = encoder;
    }

    @Override
    protected RequestTemplate resolve(Object[] argv,
                                      RequestTemplate mutable,
                                      Map<String, Object> variables) {
      final Object body = argv[metadata.bodyIndex()];
      checkArgument(body != null, "Body parameter %s was null", metadata.bodyIndex());
      try {
        encoder.encode(body, metadata.bodyType(), mutable);
      } catch (final EncodeException e) {
        throw e;
      } catch (final RuntimeException e) {
        throw new EncodeException(e.getMessage(), e);
      }
      return super.resolve(argv, mutable, variables);
    }
  }
}

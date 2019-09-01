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
package feign.aptgenerator.github;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import example.github.Contributor;
import example.github.GitHubExample.GitHub;
import example.github.Issue;
import example.github.Repository;
import feign.*;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Request.HttpMethod;
import feign.Target.HardCodedTarget;
import feign.apt.runtime.ParameterizedType;

public class GitHubFactory implements GitHub {

  private static abstract class TypeResolver<T> {
    private final Type type;

    private TypeResolver() {
      this.type =
          ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass())
              .getActualTypeArguments()[0];
    }

    public Type resolve() {
      return type;
    }

  }

  private static final MethodMetadata __GitHub_repos__metadata;
  static {
    final MethodMetadata md = new MethodMetadata();
    md.returnType(new ParameterizedType(List.class, null, Repository.class));
    md.configKey("GitHub#repos(String)");

    md.template().method(HttpMethod.GET);
    md.template().uri("/users/{username}/repos?sort=full_name");
    md.template().decodeSlash(true);
    md.template().collectionFormat(CollectionFormat.EXPLODED);

    md.indexToName().put(0, Arrays.asList("username"));
    md.indexToEncoded().put(0, false);
    __GitHub_repos__metadata = md;
  }

  private static final MethodMetadata __GitHub_contributors__metadata;
  static {
    final MethodMetadata md = new MethodMetadata();
    __GitHub_contributors__metadata = md;
    md.returnType(new ParameterizedType(List.class, null, Contributor.class));
    md.configKey("GitHub#contributors(String,String)");

    md.template().method(HttpMethod.GET);
    md.template().uri("/repos/{owner}/{repo}/contributors");
    md.template().decodeSlash(true);
    md.template().collectionFormat(CollectionFormat.EXPLODED);

    md.indexToName().put(0, Arrays.asList("owner"));
    md.indexToEncoded().put(0, false);

    md.indexToName().put(1, Arrays.asList("repo"));
    md.indexToEncoded().put(1, false);
  }

  private static final MethodMetadata __GitHub_createIssue__metadata;
  static {
    final MethodMetadata md = new MethodMetadata();
    __GitHub_createIssue__metadata = md;
    md.returnType(void.class);
    md.configKey("GitHub#createIssue(Issue,String,String)");

    md.template().method(HttpMethod.POST);
    md.template().uri("/repos/{owner}/{repo}/issues");
    md.template().decodeSlash(true);
    md.template().collectionFormat(CollectionFormat.EXPLODED);

    md.indexToName().put(0, Arrays.asList("owner"));
    md.indexToEncoded().put(0, false);

    md.indexToName().put(1, Arrays.asList("repo"));
    md.indexToEncoded().put(1, false);
  }

  private final MethodHandler __repos_handler;
  private final MethodHandler __contributors_handler;
  private final SynchronousMethodHandler __createIssue_handler;

  public GitHubFactory(FeignConfig feignConfig) {
    final HardCodedTarget<GitHub> target =
        new HardCodedTarget<GitHub>(GitHub.class, feignConfig.url);

    __repos_handler = new SynchronousMethodHandler(
        target,
        feignConfig,
        __GitHub_repos__metadata,
        ReflectiveFeign.from(__GitHub_repos__metadata, feignConfig));

    __contributors_handler = new SynchronousMethodHandler(
        target,
        feignConfig,
        __GitHub_contributors__metadata,
        ReflectiveFeign.from(__GitHub_contributors__metadata, feignConfig));

    __createIssue_handler = new SynchronousMethodHandler(
        target,
        feignConfig,
        __GitHub_contributors__metadata,
        ReflectiveFeign.from(__GitHub_contributors__metadata, feignConfig));

  }

  @Override
  public List<Repository> repos(String owner) {
    try {
      return __repos_handler.invoke(owner);
    } catch (final FeignException e) {
      throw e;
    } catch (final Throwable e) {
      throw new FeignException(-1, "", e) {};
    }
  }

  @Override
  public List<Contributor> contributors(String owner, String repo) {
    try {
      return __contributors_handler.invoke(owner, repo);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Throwable e) {
      throw new FeignException(-1, "", e) {};
    }
  }

  @Override
  public void createIssue(Issue issue, String owner, String repo) {
    try {
      __createIssue_handler.invoke(issue, owner, repo);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Throwable e) {
      throw new FeignException(-1, "", e) {};
    }

  }

}

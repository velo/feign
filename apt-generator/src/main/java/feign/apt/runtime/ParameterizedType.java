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
package feign.apt.runtime;

import java.lang.reflect.Type;
import java.util.Arrays;

public class ParameterizedType implements java.lang.reflect.ParameterizedType {

  private final Type ownerType;
  private final Type rawType;
  private final Type[] typedArgs;

  public ParameterizedType(Type rawType, Type ownerType, Type... typedArgs) {
    super();
    this.ownerType = ownerType;
    this.rawType = rawType;
    this.typedArgs = typedArgs;
  }

  @Override
  public Type[] getActualTypeArguments() {
    return this.typedArgs;
  }

  @Override
  public Type getRawType() {
    return this.rawType;
  }

  @Override
  public Type getOwnerType() {
    return this.ownerType;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((getOwnerType() == null) ? 0 : getOwnerType().hashCode());
    result = prime * result + ((getRawType() == null) ? 0 : getRawType().hashCode());
    result = prime * result + Arrays.hashCode(getActualTypeArguments());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof java.lang.reflect.ParameterizedType)) {
      return false;
    }
    final java.lang.reflect.ParameterizedType other = (java.lang.reflect.ParameterizedType) obj;
    if (getOwnerType() == null) {
      if (other.getOwnerType() != null) {
        return false;
      }
    } else if (!getOwnerType().equals(other.getOwnerType())) {
      return false;
    }
    if (getRawType() == null) {
      if (other.getRawType() != null) {
        return false;
      }
    } else if (!getRawType().equals(other.getRawType())) {
      return false;
    }
    if (!Arrays.equals(getActualTypeArguments(), other.getActualTypeArguments())) {
      return false;
    }
    return true;
  }

}

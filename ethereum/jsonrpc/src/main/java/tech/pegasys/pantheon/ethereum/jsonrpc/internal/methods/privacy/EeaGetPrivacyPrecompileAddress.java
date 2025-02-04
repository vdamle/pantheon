/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy;

import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class EeaGetPrivacyPrecompileAddress implements JsonRpcMethod {

  private final Integer privacyAddress;
  private final Boolean privacyEnabled;

  public EeaGetPrivacyPrecompileAddress(final PrivacyParameters privacyParameters) {
    privacyAddress = privacyParameters.getPrivacyAddress();
    privacyEnabled = privacyParameters.isEnabled();
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_GET_PRIVACY_PRECOMPILE_ADDRESS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {

    if (privacyEnabled) {
      return new JsonRpcSuccessResponse(request.getId(), privacyAddress);
    } else {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.PRIVACY_NOT_ENABLED);
    }
  }
}

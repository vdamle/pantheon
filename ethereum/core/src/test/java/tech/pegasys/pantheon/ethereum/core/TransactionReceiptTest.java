/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.core;

import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.ethereum.rlp.RLP;

import org.junit.Test;

public class TransactionReceiptTest {

  @Test
  public void toFromRlp() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt();
    final TransactionReceipt copy =
        TransactionReceipt.readFrom(RLP.input(RLP.encode(receipt::writeToWithRevertReason)), false);
    assertEquals(receipt, copy);
  }

  @Test
  public void toFromRlpWithReason() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt("RevertReason");
    final TransactionReceipt copy =
        TransactionReceipt.readFrom(RLP.input(RLP.encode(receipt::writeToWithRevertReason)));
    assertEquals(receipt, copy);
  }
}

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
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.RocksDbStorageProvider;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.RocksDbConfiguration;
import tech.pegasys.pantheon.services.tasks.CachingTaskCollection;
import tech.pegasys.pantheon.services.tasks.FlatFileTaskCollection;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Thread)
public class WorldStateDownloaderBenchmark {

  private final BlockDataGenerator dataGen = new BlockDataGenerator();
  private Path tempDir;
  private BlockHeader blockHeader;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private WorldStateDownloader worldStateDownloader;
  private WorldStateStorage worldStateStorage;
  private RespondingEthPeer peer;
  private Responder responder;
  private CachingTaskCollection<NodeDataRequest> pendingRequests;
  private StorageProvider storageProvider;
  private EthProtocolManager ethProtocolManager;
  private InMemoryKeyValueStorage remoteKeyValueStorage;

  @Setup(Level.Invocation)
  public void setUpUnchangedState() throws Exception {
    final SynchronizerConfiguration syncConfig =
        new SynchronizerConfiguration.Builder().worldStateHashCountPerRequest(200).build();
    final Hash stateRoot = createExistingWorldState();
    blockHeader = new BlockHeaderTestFixture().stateRoot(stateRoot).buildHeader();

    tempDir = Files.createTempDir().toPath();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            new EthScheduler(
                syncConfig.getDownloaderParallelism(),
                syncConfig.getTransactionsParallelism(),
                syncConfig.getComputationParallelism(),
                metricsSystem));

    peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, blockHeader.getNumber());

    final EthContext ethContext = ethProtocolManager.ethContext();
    storageProvider =
        RocksDbStorageProvider.create(
            RocksDbConfiguration.builder().databaseDir(tempDir.resolve("database")).build(),
            metricsSystem);
    worldStateStorage = storageProvider.createWorldStateStorage();

    pendingRequests =
        new CachingTaskCollection<>(
            new FlatFileTaskCollection<>(
                tempDir.resolve("fastsync"),
                NodeDataRequest::serialize,
                NodeDataRequest::deserialize),
            0);
    worldStateDownloader =
        new WorldStateDownloader(
            ethContext,
            worldStateStorage,
            pendingRequests,
            syncConfig.getWorldStateHashCountPerRequest(),
            syncConfig.getWorldStateRequestParallelism(),
            syncConfig.getWorldStateMaxRequestsWithoutProgress(),
            syncConfig.getWorldStateMinMillisBeforeStalling(),
            Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC),
            metricsSystem);
  }

  private Hash createExistingWorldState() {
    // Setup existing state
    remoteKeyValueStorage = new InMemoryKeyValueStorage();
    final WorldStateStorage storage = new WorldStateKeyValueStorage(remoteKeyValueStorage);
    final WorldStateArchive worldStateArchive = new WorldStateArchive(storage);
    final MutableWorldState worldState = worldStateArchive.getMutable();

    dataGen.createRandomAccounts(worldState, 10000);

    responder = RespondingEthPeer.blockchainResponder(null, worldStateArchive);
    return worldState.rootHash();
  }

  @TearDown(Level.Invocation)
  public void tearDown() throws Exception {
    ethProtocolManager.stop();
    ethProtocolManager.awaitStop();
    pendingRequests.close();
    storageProvider.close();
    MoreFiles.deleteRecursively(tempDir, RecursiveDeleteOption.ALLOW_INSECURE);
  }

  @Benchmark
  public Optional<BytesValue> downloadWorldState() {
    final CompletableFuture<Void> result = worldStateDownloader.run(blockHeader);
    if (result.isDone()) {
      throw new IllegalStateException("World state download was already complete");
    }
    peer.respondWhileOtherThreadsWork(responder, () -> !result.isDone());
    result.getNow(null);
    final Optional<BytesValue> rootData = worldStateStorage.getNodeData(blockHeader.getStateRoot());
    if (!rootData.isPresent()) {
      throw new IllegalStateException("World state download did not complete.");
    }
    return rootData;
  }
}

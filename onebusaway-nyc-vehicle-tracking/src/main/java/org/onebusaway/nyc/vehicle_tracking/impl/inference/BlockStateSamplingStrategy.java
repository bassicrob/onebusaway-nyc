package org.onebusaway.nyc.vehicle_tracking.impl.inference;

import java.util.Set;

import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.BlockState;
import org.onebusaway.nyc.vehicle_tracking.impl.particlefilter.CDFMap;
import org.onebusaway.transit_data_federation.services.blocks.BlockInstance;

public interface BlockStateSamplingStrategy {

  /****
   *
   ****/

  public BlockState sampleBlockStateAtJourneyStart(
      Set<BlockInstance> potentialBlocks, Observation observation);

  public CDFMap<BlockState> blockStateCdfAtJourneyStart(
      Set<BlockInstance> potentialBlocks, Observation observation);

  public BlockState sampleBlockStateAtJourneyStart(
      Set<BlockInstance> potentialBlocks, Observation observation,
      BlockState currentBlockState);

  public BlockState sampleBlockStateForJourneyInProgress(
      Set<BlockInstance> potentialBlocks, Observation observation);

}
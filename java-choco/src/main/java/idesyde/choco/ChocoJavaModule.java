package idesyde.choco;

class ChocoJavaModule extends StandaloneModule {
  
  // def combination(decisionModel: DecisionModel): ExplorationCombinationDescription = {
  //   val combos = explorers.map(e => e.combination(decisionModel))
  //   // keep only the dominant ones and take the biggest
  //   combos
  //     .filter(l => {
  //       combos
  //         .filter(_ != l)
  //         .forall(r => {
  //           l `<?>` r match {
  //             case '>' | '=' => true
  //             case _         => false
  //           }
  //         })
  //     })
  //     .head
  // }

  @Override
  public String uniqueIdentifier() {
    return "ChocoJavaModule";
  }

  @Override
    public Optional<DecisionModel> fromOpaqueDecision(OpaqueDecisionModel message) {
        switch (message.category()) {
            case "AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore":
                return message.bodyCBOR().flatMap(x -> readFromCBORBytes(x, AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class))
                        .or(() -> message.bodyJson().flatMap(x -> readFromJsonString(x, AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class)))
                        .map(x -> (DecisionModel) x);
            case "AperiodicAsynchronousDataflowToPartitionedTiledMulticore":
                return message.bodyCBOR().flatMap(x -> readFromCBORBytes(x, AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class))
                        .or(() -> message.bodyJson().flatMap(x -> readFromJsonString(x, AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class)))
                        .map(x -> (DecisionModel) x);
            default:
                return Optional.empty();
        }
    }

  // def decisionMessageToModel(m: DecisionModelMessage): Option[DecisionModel] = {
  //   m.header match {
  //     case DecisionModelHeader("SDFToTiledMultiCore", _, _) =>
  //       m.body.map(s => read[SDFToTiledMultiCore](s))
  //     case DecisionModelHeader("PeriodicWorkloadToPartitionedSharedMultiCore", _, _) =>
  //       m.body.map(s => read[PeriodicWorkloadToPartitionedSharedMultiCore](s))
  //     case DecisionModelHeader("PeriodicWorkloadAndSDFServerToMultiCore", _, _) =>
  //       m.body.map(s => read[PeriodicWorkloadAndSDFServerToMultiCore](s))
  //     case _ => None
  //   }
  // }

  public static void main(String[] args) {
      standaloneModule(args).ifPresent(javalin -> javalin.start(0));
  }

}
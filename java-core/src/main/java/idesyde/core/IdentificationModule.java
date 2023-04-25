package idesyde.core;

import idesyde.core.headers.DecisionModelHeader;
import idesyde.core.headers.DesignModelHeader;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/** The trait/interface for an identification module that provides the identification and
 * integration rules required to power the design space identification process [1].
 *
 * This trait extends [[idesyde.core.IdentificationLibrary]] to push further the modularization of
 * the DSI methodology. In essence, this trait transforms an [[idesyde.core.IdentificationLibrary]]
 * into an independent callable library, which can be orchestrated externally. This enables modules
 * in different languages to cooperate seamlessly.
 *
 * @see
 *   [[idesyde.core.IdentificationLibrary]]
 */
public interface IdentificationModule extends Callable<Integer> {


    /** Unique string used to identify this module during orchetration. Ideally it matches the name of
     * the implementing class (or is the implemeting class name, ditto).
     */
    String uniqueIdentifier();

    default Optional<DesignModel> inputsToDesignModel(Path p) { return Optional.empty(); }

    /** decoders used to reconstruct decision models from headers.
     *
     * Ideally, these functions are able to produce a decision model from the headers read during a
     * call of this module.
     *
     * @return
     *   the registered decoders
     */
    Optional<DecisionModel> decisionHeaderToModel(DecisionModelHeader header);

    Optional<DesignModel> designHeaderToModel(DesignModelHeader header);

    default Optional<Path> designModelToOutput(DesignModel m, Path p) {return Optional.empty(); }

    Set<ReverseIdentificationRule<?>> reverseIdentificationRules();

    Set<IdentificationRule<?>> identificationRules();

    default Set<DesignModel> reverseIdentification(
            Set<DesignModel> designModels,
            Set<DecisionModel> solvedDecisionModels
    ) {
        return reverseIdentificationRules().stream().flatMap(irrule ->
                irrule.apply(solvedDecisionModels, designModels).stream()
        ).collect(Collectors.toSet());
    }

    default Set<DecisionModel> identificationStep(
            long stepNumber,
            Set<DesignModel> designModels,
            Set<DecisionModel> decisionModels
    ) {
        if (stepNumber == 0L) {
            return identificationRules().stream().filter(IdentificationRule::usesDesignModels)
                    .flatMap(identificationRule -> identificationRule.apply(designModels, decisionModels).stream())
                    .filter(m -> !decisionModels.contains(m))
                    .collect(Collectors.toSet());
        } else {
            return identificationRules().stream().filter(IdentificationRule::usesDecisionModels)
                    .flatMap(identificationRule -> identificationRule.apply(designModels, decisionModels).stream())
                    .filter(m -> !decisionModels.contains(m))
                    .collect(Collectors.toSet());
        }
    }

    default void standaloneIdentificationModule(
            args: Array<String>
    ) {
        val parser = ArgParser("I-Module " + uniqueIdentifier())
        val designPathStr = parser.option(ArgType.String, shortName = "m", fullName = "design-path", description = "The path where the design models (and headers) are stored.")
        val identifiedPathStr = parser.option(ArgType.String, shortName = "i", fullName = "identified-path", description = "The path where identified decision models (and headers) are stored.")
        val solvedPathStr = parser.option(ArgType.String, shortName = "s", fullName = "solved-path", description = "The path where explored decision models (and headers) are stored.")
        val reversePathStr = parser.option(ArgType.String, shortName = "r", fullName = "reverse-path", description = "The path where reverse identified design models (and headers) are stored.")
        val outputPath = parser.option(ArgType.String, shortName = "o", fullName = "output-path", description = "The path where final integrated design models are stored, in their original format.")
        val identStep = parser.option(ArgType.Int, shortName = "t", fullName = "identification-step", description = "The overall identification iteration number.")
        parser.parse(args)
        designPathStr.value?.let {designPathStrv ->
                val designPath = Paths.get(designPathStrv)
            Files.createDirectories(designPath)
            val fromDesignHeaders = Files.list(designPath)
                    .filter { f -> f.startsWith("header") }
                .filter { f -> f.extension == ".msgpack" }
                .map { f -> MsgPack.decodeFromByteArray(DesignModelHeader.serializer(), Files.readAllBytes(f)) }
                .map { f -> designHeaderToModel(f) }
                .toList()
                    .filterNotNull()
                    .toSet()
            val directFromFile = Files.list(designPath)
                    .map { f ->
                    val m = inputsToDesignModel(f)
                m?.header()?.let {
                    val h = it.copy(modelPaths = listOf(f.toString()))
                    val dest = designPath.resolve(Paths.get("header_%s_%s", h.category, uniqueIdentifier()))
                    Files.writeString(dest.resolve(".json"), Json.encodeToString(DesignModelHeader.serializer(), h))
                    Files.write(dest.resolve(".msgpack"), MsgPack.encodeToByteArray(DesignModelHeader.serializer(), h))
                }
                m
            }
                .filter { f -> f != null }
                .toList()
                    .filterNotNull()
                    .toSet()
            val designModels = fromDesignHeaders.union(directFromFile)

            solvedPathStr.value?.let { solvedPathv ->
                    val solvedPath = Paths.get(solvedPathv)
                reversePathStr.value?.let {reversePathv ->
                        val reversePath = Paths.get(reversePathv)
                    Files.createDirectories(solvedPath)
                    Files.createDirectories(reversePath)
                    val solvedDecisionModels = Files.list(solvedPath)
                            .filter { f -> f.startsWith("header") }
                        .filter { f -> f.extension == ".msgpack" }
                        .map { f -> MsgPack.decodeFromByteArray(DecisionModelHeader.serializer(), Files.readAllBytes(f)) }
                        .map { f -> decisionHeaderToModel(f) }
                        .toList()
                            .filterNotNull()
                            .toSet()
                    val reIdentified = reverseIdentification(designModels, solvedDecisionModels)
                    for ((i, m) in reIdentified.withIndex()) {
                        val dest = reversePath.resolve(i.toString()).resolve(uniqueIdentifier()).resolve(".msgpack")
                        val header = m.header()
                        val h = outputPath.value?.let { op ->
                                val saved = designModelToOutput(m, Paths.get(op))
                            header.copy(modelPaths = listOf(saved.toString()))
                        } ?: header
                        Files.write(dest, MsgPack.encodeToByteArray(DesignModelHeader.serializer(), h))
                    }
                }
            }
            identifiedPathStr.value?.let {identifiedPathv ->
                    val identifiedPath = Paths.get(identifiedPathv)
                Files.createDirectories(identifiedPath)
                val decisionModels = Files.list(identifiedPath)
                        .filter { f -> f.startsWith("header") }
                    .filter { f -> f.extension == ".msgpack" }
                    .map { f -> MsgPack.decodeFromByteArray(DecisionModelHeader.serializer(), Files.readAllBytes(f)) }
                    .map { f -> decisionHeaderToModel(f) }
                    .toList()
                        .filterNotNull()
                        .toSet()
                val identified = identificationStep(
                        identStep.value?.toLong() ?: 0L,
                        designModels,
                        decisionModels
                )
                for (m in identified) {
                    val header = m.header()
                    val destH = identifiedPath.resolve("header_%016d_%s_%s".format(identStep, header.category, uniqueIdentifier()))
                    val h = if (m is DecisionModelWithBody) {
                        val destB = identifiedPath.resolve("body_%016d_%s_%s".format(identStep, header.category, uniqueIdentifier()))
                        Files.writeString(destB.resolve(".json"), m.getBodyAsText())
                        Files.write(destB.resolve(".msgpack"), m.getBodyAsBytes())
                        header.copy(bodyPath = "body_%016d_%s_%s.msgpack".format(identStep, header.category, uniqueIdentifier()))
                    } else header
                    Files.writeString(destH.resolve(".json"), Json.encodeToString(DecisionModelHeader.serializer(), h))
                    Files.write(destH.resolve(".msgpack"), MsgPack.encodeToByteArray(DecisionModelHeader.serializer(), h))
                }
            }
        }
    }

}

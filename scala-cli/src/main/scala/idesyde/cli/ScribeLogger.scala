package idesyde.cli

// import idesyde.utils.Logger
// import scribe.format.FormatterInterpolator
// import scribe.Level
// import scribe.file._
// import java.io.File
// import scala.collection.mutable.Buffer

// @deprecated
// object ScribeLogger extends Logger {

//   override def setLoggingLevel(lvl: String): Logger =
//     setLoggingLevel(Level.get(lvl).get, Buffer.empty)

//   override def debug(s: String): Unit = scribe.debug(s)
//   override def info(s: String): Unit  = scribe.info(s)
//   override def warn(s: String): Unit  = scribe.warn(s)
//   override def error(s: String): Unit = scribe.error(s)

//   def setLoggingLevel(loggingLevel: Level, additionalFiles: Buffer[File] = Buffer.empty): Logger = {
//     var builder = scribe.Logger.root
//       .clearHandlers()
//       .clearModifiers()
//     if (loggingLevel == Level.Debug) {
//       builder = builder
//         .withHandler(
//           minimumLevel = Some(loggingLevel),
//           formatter =
//             formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format
//               .italic(scribe.format.classNameSimple)} - ${scribe.format.messages}"
//         )
//       for (outlet <- additionalFiles) {
//         builder = builder.withHandler(
//           minimumLevel = Some(loggingLevel),
//           formatter =
//             formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format
//               .italic(scribe.format.classNameSimple)} - ${scribe.format.messages}",
//           writer = FileWriter(outlet)
//         )
//       }
//     } else {
//       builder = builder
//         .withHandler(
//           minimumLevel = Some(loggingLevel),
//           formatter =
//             formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.messages}"
//         )
//       for (outlet <- additionalFiles) {
//         builder = builder.withHandler(
//           minimumLevel = Some(loggingLevel),
//           formatter =
//             formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.messages}",
//           writer = FileWriter(outlet)
//         )
//       }
//     }
//     builder.replace()
//   }
// }

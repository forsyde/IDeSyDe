package mixins

import scribe.Level
import scribe.format.FormatterInterpolator
import scribe.format.FormatBlock

trait LoggingMixin {

  def setWarning() = scribe.Logger.root
    .clearHandlers()
    .clearModifiers()
    .withHandler(minimumLevel = Some(Level.Warn), formatter = formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.message}")
    .replace()
  
  def setNormal() = scribe.Logger.root
    .clearHandlers()
    .clearModifiers()
    .withHandler(minimumLevel = Some(Level.Info), formatter = formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.message}")
    .replace()

  def setDebug() = scribe.Logger.root
    .clearHandlers()
    .clearModifiers()
    .withHandler(minimumLevel = Some(Level.Debug), formatter = formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.italic(scribe.format.classNameSimple)} - ${scribe.format.message}")
    .replace()
}

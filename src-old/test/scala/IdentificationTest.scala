import forsyde.io.java.drivers.ForSyDeSystemGraphHandler
import idesyde.identification.api.Identification
class IdentificationTest {
  
    def testeasy() = {
        val model = ForSyDeSystemGraphHandler().loadModel("FlightInformationFunctionReactor.forxml")
        val dm = Identification.identifyDecisionModels(model)
    }
}

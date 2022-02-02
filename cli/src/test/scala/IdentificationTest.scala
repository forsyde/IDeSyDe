import idesyde.identification.api.Identification
import forsyde.io.java.drivers.ForSyDeModelHandler
class IdentificationTest {
  
    def testeasy() = {
        val model = ForSyDeModelHandler().loadModel("FlightInformationFunctionReactor.forxml")
        val dm = Identification.identifyDecisionModels(model)
    }
}

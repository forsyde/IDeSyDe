import idesyde.identification.forsyde.api.IdentificationHandler
import forsyde.io.java.drivers.ForSyDeModelHandler
class IdentificationTest {
  
    def testeasy() = {
        val model = ForSyDeModelHandler().loadModel("FlightInformationFunctionReactor.forxml")
        val dm = IdentificationHandler().identifyDecisionModels(model)
    }
}

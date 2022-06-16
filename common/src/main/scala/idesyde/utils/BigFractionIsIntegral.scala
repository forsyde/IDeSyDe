package idesyde.utils

import org.apache.commons.math3.fraction.BigFraction

class BigFractionIsIntegral extends BigFractionIsNumeric with Integral[BigFraction]  {

    def quot(x: BigFraction, y: BigFraction):
        BigFraction = BigFraction(x.divide(y).doubleValue.floor)
  
    def rem(x: BigFraction, y: BigFraction):
        BigFraction = {
            val resDiv = x.divide(y)
            BigFraction(resDiv.getNumeratorAsLong % resDiv.getDenominatorAsLong)
        }
    
}
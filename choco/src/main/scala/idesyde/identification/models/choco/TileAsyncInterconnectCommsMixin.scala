package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoModelMixin
import idesyde.identification.models.platform.TiledMultiCorePlatformMixin
import spire.algebra._   
import spire.math._      
import spire.implicits._ 

trait TileAsyncInterconnectCommsMixin extends ChocoModelMixin {

    def platform: TiledMultiCorePlatformMixin[Long, Rational]
    def numDataSent: Int
    def dataSizes(dataIdx: Int): Long

  
}

package idesyde.utils

package object choco {
  
    // credits to https://august.nagro.us/scala-for-loop.html
    inline def wfor[A](
        inline start: A,
        inline condition: A => Boolean,
        inline advance: A => A
    )(inline loopBody: A => Any): Unit =
      var a = start
      while condition(a) do
        loopBody(a)
        a = advance(a)
}

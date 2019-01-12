module Exploration.General where

class Symmetric a where
  (~=~) :: a -> a -> Bool
  (~/~) :: a -> a -> Bool
  (~/~) a a' = not $ a ~=~ a'

class Valued a where
  value :: (Ord v) => a -> v

minBy :: (Ord v) => (a -> v) -> [a] -> a
minBy _ [a] = a
minBy f (a:as)
  | f a < f m = a
  | f a > f m = m
  where m = minBy f as

symmetricSets :: (Symmetric a) => [a] -> [[a]]
symmetricSets [] = []
symmetricSets [a] = [[a]]
symmetricSets (a:as) = let
  equivalents = a : [a' | a' <- as, a' ~=~ a]
  nonequiv = [a' | a' <- as, a' ~/~ a]
  in
  equivalents : symmetricSets nonequiv

symmetricPairSets :: (Symmetric a) => [(x, a)] -> [[(x, a)]]
symmetricPairSets [] = []
symmetricPairSets [(x, a)] = [[(x, a)]]
symmetricPairSets ((x, a):xas) = let
  equivalents = (x, a) : [(x', a') | (x', a') <- xas, a' ~=~ a]
  nonequiv = [(x', a') | (x', a') <- xas, a' ~/~ a]
  in
  equivalents : symmetricPairSets nonequiv

searchGoal :: (Symmetric a, Valued a) =>
  [(a -> x -> [a])] -> [([x], a)] -> [([x], a)]
searchGoal [] _ = []
searchGoal _ [] = []
searchGoal rs j_k
  | head j_kp == ([], _) = j_kp
  | otherwise = searchGoal rs j_kp
  where
  s_kp = concat [(xs, a') | r <- rs, (x:xs, a) <- j_k, a' <- r a x]
  j_kp = map minBy . (\(xs,a) -> value a) $ symmetricPairSets s_kp
    
  


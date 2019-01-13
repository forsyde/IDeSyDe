module Exploration.General where

class Symmetric a where
  (~==~) :: a -> a -> Bool
  (~/=~) :: a -> a -> Bool
  (~/=~) a a' = not $ a ~==~ a'

class Valued a where
  value :: (Ord v) => a -> v

minBy :: (Ord v) => (a -> v) -> [a] -> a
minBy _ [a] = a
minBy f (a:as)
  | f a < f m = a
  | f a > f m = m
  where m = minBy f as

dominantSymmetricSet :: (Symmetric a, Valued a) => [(xs, a)] -> [(xs, a)]
dominantSymmetricSet [] = []
dominantSymmetricSet ((xs, a):xas) = let
  equivalents = (xs, a) : [(xs', a') | (xs', a') <- xas, a' ~==~ a]
  nonequiv = [(xs', a') | (xs', a') <- xas, a' ~/=~ a]
  best = minBy (value . snd) equivalents
  in
  best : dominantSymmetricSet nonequiv

searchGoal :: (Symmetric a, Valued a) =>
  [(a -> x -> [a])] -> [([x], a)] -> [([x], a)]
searchGoal [] _ = []
searchGoal _ [] = []
searchGoal rs j_k
  | head j_k == ([], _) = j_k
  | otherwise = searchGoal rs j_kp
  where
  j_kp = dominantSymmetricSet [(xs, a') | r <- rs, (x:xs, a) <- j_k, a' <- r a x]

symmetricSets :: (Symmetric a) => [a] -> [[a]]
symmetricSets [] = []
symmetricSets [a] = [[a]]
symmetricSets (a:as) = let
  equivalents = a : [a' | a' <- as, a' ~==~ a]
  nonequiv = [a' | a' <- as, a' ~/=~ a]
  in
  equivalents : symmetricSets nonequiv


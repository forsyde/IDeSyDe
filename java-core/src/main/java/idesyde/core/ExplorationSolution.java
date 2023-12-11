package idesyde.core;

import java.util.Map;

public record ExplorationSolution(
                Map<String, Double> objectives,
                DecisionModel solved) {

        public boolean dominates(ExplorationSolution other) {
                return objectives.entrySet()
                                .stream()
                                .allMatch(e -> other.objectives().containsKey(e.getKey())
                                                && e.getValue() <= other.objectives.get(e.getKey()))
                                && objectives.entrySet()
                                                .stream()
                                                .anyMatch(e -> other.objectives().containsKey(e.getKey())
                                                                && e.getValue() < other.objectives.get(e.getKey()));
        }

        public java.util.Optional<byte[]> globalMD5Hash() {
                try {
                        var md5 = java.security.MessageDigest.getInstance("MD5");
                        md5.update(solved().globalMD5Hash().orElse(new byte[0]));
                        objectives().keySet().stream().sorted().forEach(k -> {
                                md5.update(k.getBytes());
                                md5.update(Double.toString(objectives().get(k)).getBytes());
                        });
                        return java.util.Optional.of(md5.digest());
                } catch (java.security.NoSuchAlgorithmException e) {
                        return java.util.Optional.empty();
                }
        }

        public java.util.Optional<byte[]> globalSHA2Hash() {
                try {
                        var md5 = java.security.MessageDigest.getInstance("MD5");
                        md5.update(solved().globalSHA2Hash().orElse(new byte[0]));
                        objectives().keySet().stream().sorted().forEach(k -> {
                                md5.update(k.getBytes());
                                md5.update(Double.toString(objectives().get(k)).getBytes());
                        });
                        return java.util.Optional.of(md5.digest());
                } catch (java.security.NoSuchAlgorithmException e) {
                        return java.util.Optional.empty();
                }
        }
}

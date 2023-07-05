package idesyde.matlab;

public record SimulinkArc(
         String src,
         String dst,
         String srcPort,
         String dstPort,
         Long dataSize
) {
}

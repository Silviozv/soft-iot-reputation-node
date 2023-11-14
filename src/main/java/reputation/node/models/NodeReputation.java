package reputation.node.models;

/**
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class NodeReputation {

  private String nodeId;
  private Double reputation;

  /**
   * Método construtor.
   *
   * @param nodeId String - ID do nó.
   * @param reputation Double - Reputação do nó.
   */
  public NodeReputation(String nodeId, Double reputation) {
    this.nodeId = nodeId;
    this.reputation = reputation;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public Double getReputation() {
    return reputation;
  }

  public void setReputation(Double reputation) {
    this.reputation = reputation;
  }
}

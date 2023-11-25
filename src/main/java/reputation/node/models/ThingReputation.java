package reputation.node.models;

/**
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class ThingReputation {

  private String thingId;
  private Double reputation;

  /**
   * Método construtor.
   *
   * @param thingId String - Identificador.
   * @param reputation Double - Reputação do nó.
   */
  public ThingReputation(String thingId, Double reputation) {
    this.thingId = thingId;
    this.reputation = reputation;
  }

  public String getNodeId() {
    return thingId;
  }

  public void setNodeId(String thingId) {
    this.thingId = thingId;
  }

  public Double getReputation() {
    return reputation;
  }

  public void setReputation(Double reputation) {
    this.reputation = reputation;
  }
}

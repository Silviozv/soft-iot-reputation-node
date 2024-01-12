package reputation.node.models;

/**
 * Classe responsável por relacionar o ID de uma 'coisa' com sua respectiva
 * reputação.
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

  public String getThingId() {
    return thingId;
  }

  public void setThingId(String thingId) {
    this.thingId = thingId;
  }

  public Double getReputation() {
    return reputation;
  }

  public void setReputation(Double reputation) {
    this.reputation = reputation;
  }
}

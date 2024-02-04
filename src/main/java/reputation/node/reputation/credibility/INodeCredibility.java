package reputation.node.reputation.credibility;

/**
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public interface INodeCredibility {
  /**
   * Obtém a credibilidade mais recente de um nó.
   *
   * @param nodeId String - ID do nó que se deseja saber a credibilidade.
   * @return float
   */
  public float get(String nodeId);
}

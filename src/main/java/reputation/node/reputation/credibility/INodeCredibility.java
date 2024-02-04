package reputation.node.reputation.credibility;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import java.util.List;
import reputation.node.models.SourceCredibility;

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

  /**
   * Obtém e adiciona em uma lista, os valores da credibilidade mais recente dos
   * nós avaliadores cujos IDs foram informados pela lista de transações de
   * avaliações.
   *
   * @param serviceProviderEvaluationTransactions List<Transaction> - Lista de
   * transações de avaliações.
   * @param sourceId String - ID do atual nó avaliador.
   * @return List<SourceCredibility>
   */
  public List<SourceCredibility> getNodesEvaluatorsCredibility(
    List<Transaction> serviceProviderEvaluationTransactions,
    String sourceId
  );
}

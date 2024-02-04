package reputation.node.reputation.credibility;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.model.transactions.reputation.Credibility;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import reputation.node.tangle.LedgerConnector;

/**
 * Responsável por lidar com a credibilidade do nó.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public final class NodeCredibility implements INodeCredibility {

  private LedgerConnector ledgerConnector;

  public NodeCredibility() {}

  /**
   * Obtém a credibilidade mais recente de um nó.
   *
   * @param nodeId String - ID do nó que se deseja saber a credibilidade.
   * @return float
   */
  public float get(String nodeId) {
    List<Transaction> tempCredibility =
      this.ledgerConnector.getLedgerReader()
        .getTransactionsByIndex("cred_" + nodeId, false);

    float nodeCredibility = Optional
      .ofNullable(tempCredibility)
      .map(transactions ->
        transactions
          .stream()
          .sorted((t1, t2) ->/* Ordenando em ordem decrescente. */
            Long.compare(t2.getCreatedAt(), t1.getCreatedAt())
          )
          .collect(Collectors.toList())
      )
      .filter(list -> !list.isEmpty())
      /* Obtendo a credibilidade mais recente calculada pelo nó */
      .map(list -> ((Credibility) list.get(0)).getValue())
      /* Caso o nó ainda não tenha calculado a sua credibilidade, por padrão é 0.5. */
      .orElse((float) 0.5);

    return nodeCredibility;
  }

  public LedgerConnector getLedgerConnector() {
    return ledgerConnector;
  }

  public void setLedgerConnector(LedgerConnector ledgerConnector) {
    this.ledgerConnector = ledgerConnector;
  }
}

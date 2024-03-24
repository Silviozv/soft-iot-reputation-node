package reputation.node.tasks;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import java.util.List;
import java.util.TimerTask;
import reputation.node.models.Node;
import reputation.node.reputation.IReputation;

/**
 * Task para calcular a reputação atual do nó.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class CalculateNodeReputationTask extends TimerTask {

  private final Node node;
  private final IReputation reputation;

  /**
   * Método construtor.
   * @param node Node - O nó que verificará a própria reputação.
   * @param reputation IReputation - Objeto para calcular a reputação.
   */
  public CalculateNodeReputationTask(Node node, IReputation reputation) {
    this.node = node;
    this.reputation = reputation;
  }

  @Override
  public void run() {
    List<Transaction> evaluationTransactions =
      this.node.getLedgerConnector()
        .getLedgerReader()
        .getTransactionsByIndex(this.node.getNodeType().getNodeId(), false);

    double reputationValue =
      this.reputation.calculate(
          evaluationTransactions,
          this.node.isUseLatestCredibility(),
          this.node.isUseCredibility()
        );

    this.node.setReputationValue(reputationValue);
  }
}

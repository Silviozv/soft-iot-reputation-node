package reputation.node.reputation;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.model.transactions.reputation.Evaluation;
import java.util.List;

/**
 * Responsável por calcular a reputação de uma coisa.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class ReputationCalc implements IReputationCalc {

  @Override
  public Double calc(List<Transaction> evaluationTransactions) {
    return evaluationTransactions
      .stream()
      .mapToInt(et -> ((Evaluation) et).getValue())
      .average()
      .orElse(0.0);
  }
}

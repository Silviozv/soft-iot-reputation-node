package reputation.node.reputation;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import java.util.List;

/**
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public interface IReputation {
  /**
   * Calcula a reputação de uma coisa.
   *
   * @param evaluationTransactions List<Transaction> - Lista com as transações
   * de avaliação da coisa.
   * @return Double
   */
  Double calc(List<Transaction> evaluationTransactions);
}

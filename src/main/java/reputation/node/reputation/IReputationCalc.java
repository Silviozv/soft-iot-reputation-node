package reputation.node.reputation;

import java.util.List;

import dlt.client.tangle.hornet.model.transactions.Transaction;

/**
 * 
 * @author Allan Capistrano
 * @version 1.0.0
 */
public interface IReputationCalc {

  /**
   * Calcula a reputação de uma coisa.
   * 
   * @param evaluationTransactions List<Transaction> - Lista com as transações
   * de avaliação da coisa.
   * @return Double
   */
  Double calc(List<Transaction> evaluationTransactions);
}
